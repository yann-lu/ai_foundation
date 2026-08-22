package com.ai.foundation.mediator.conversation;

import com.ai.foundation.biz.converter.ConversationConverter;
import com.ai.foundation.biz.converter.MessageConverter;
import com.ai.foundation.biz.conversation.AgentConversationService;
import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.biz.skill.AgentSkillDefinitionService;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.conversation.ConversationCreateRequest;
import com.ai.foundation.facade.dto.conversation.ConversationDTO;
import com.ai.foundation.facade.dto.conversation.ConversationDetailDTO;
import com.ai.foundation.facade.dto.conversation.ConversationPageRequest;
import com.ai.foundation.facade.dto.conversation.MessageDTO;
import com.ai.foundation.mediator.model.AgentModelResolver;
import com.ai.foundation.mediator.chat.ChatHistoryComposer;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationMedService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;
    private final AgentProjectService projectService;
    private final AgentSkillDefinitionService skillDefinitionService;
    private final AgentModelResolver modelResolver;
    private final ConversationConverter conversationConverter;
    private final MessageConverter messageConverter;
    private final ChatHistoryComposer chatHistoryComposer;
    private final ObjectMapper objectMapper;

    public ConversationDTO create(ConversationCreateRequest request) {
        AgentProject project = projectService.getByCode(request.getProductCode());
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品编码对应的项目不存在: " + request.getProductCode());
        }

        Map<String, Object> mergedContext = mergeAndValidateContext(request, project.getId());

        AgentConversationInfo entity = new AgentConversationInfo();
        entity.setProjectId(project.getId());
        entity.setProductCode(request.getProductCode());
        entity.setConversationCode(ConversationCodeGenerator.generate());
        entity.setUserId(request.getUserId() != null ? request.getUserId() : 0L);
        entity.setContextVariables(serializeContextVariables(mergedContext));
        entity.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() : "新会话");
        entity.setModelProvider("openai");
        entity.setModelName(resolveModelNameForCreate(request.getModelName(), project.getId()));
        entity.setIsPin(0);
        entity.setState(0);
        conversationService.save(entity);
        log.info("创建会话成功 code={} projectId={} model={} contextKeys={}",
                entity.getConversationCode(), entity.getProjectId(), entity.getModelName(), mergedContext.keySet());
        return conversationConverter.toDto(entity);
    }

    /**
     * 合并 KV 池 + 校验 skill 模板里出现的用户级必传 key。
     *
     * @return 合并后的 KV Map（key=value 一定有值或被显式置 null）
     */
    private Map<String, Object> mergeAndValidateContext(ConversationCreateRequest request, Long projectId) {
        Map<String, Object> merged = new HashMap<>();
        if (request.getContextVariables() != null) {
            merged.putAll(request.getContextVariables());
        }
        Set<String> required = skillDefinitionService.collectUserRequiredContextKeys(projectId);
        if (!CollectionUtils.isEmpty(required)) {
            List<String> missing = required.stream()
                    .filter(k -> !merged.containsKey(k) || isBlankValue(merged.get(k)))
                    .collect(Collectors.toList());
            if (!missing.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "创建会话缺少必需上下文: " + String.join(", ", missing));
            }
        }
        return merged;
    }

    private boolean isBlankValue(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private String serializeContextVariables(Map<String, Object> ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(ctx);
        } catch (JsonProcessingException ex) {
            log.warn("序列化会话上下文失败, 退化写空对象", ex);
            return "{}";
        }
    }

    public PageResult<ConversationDTO> page(ConversationPageRequest request) {
        Page<AgentConversationInfo> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<AgentConversationInfo> result = conversationService.page(page,
                request.getProjectId(), request.getProductCode(), request.getTitle(), request.getState());
        List<ConversationDTO> records = result.getRecords().stream().map(conversationConverter::toDto).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ConversationDetailDTO detail(Long id) {
        AgentConversationInfo conversation = conversationService.getById(id);
        if (conversation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "会话不存在");
        }
        ConversationDetailDTO detail = new ConversationDetailDTO();
        detail.setConversation(conversationConverter.toDto(conversation));

        List<AgentMessageInfo> messages = messageService.recentMessages(id, DEFAULT_MESSAGE_LIMIT);
        java.util.Collections.reverse(messages);
        detail.setMessages(messages.stream().map(messageConverter::toDto).toList());
        return detail;
    }

    public void delete(Long id) {
        AgentConversationInfo conversation = conversationService.getById(id);
        if (conversation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "会话不存在");
        }
        conversationService.removeById(id);
        messageService.softDeleteByConversationId(id);
        chatHistoryComposer.clearCache(id);
        log.info("删除会话 id={} 含消息", id);
    }

    public void clearMessages(Long id) {
        AgentConversationInfo conversation = conversationService.getById(id);
        if (conversation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "会话不存在");
        }
        messageService.softDeleteByConversationId(id);
        conversation.setLastMessageTime(null);
        conversation.setSummary(null);
        conversationService.updateById(conversation);
        chatHistoryComposer.clearCache(id);
        log.info("清空会话消息 id={}", id);
    }

    public AgentConversationInfo requireByCode(String conversationCode) {
        AgentConversationInfo conversation = conversationService.getByCode(conversationCode);
        if (conversation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "会话不存在: " + conversationCode);
        }
        return conversation;
    }

    public AgentConversationInfo requireById(Long conversationId) {
        AgentConversationInfo conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "会话不存在: " + conversationId);
        }
        return conversation;
    }

    public List<MessageDTO> messages(String conversationCode, Long beforeId, int limit) {
        AgentConversationInfo conversation = requireByCode(conversationCode);
        int size = limit > 0 && limit <= 100 ? limit : 50;
        List<AgentMessageInfo> messages = messageService.scrollMessages(
                conversation.getId(), beforeId, size);
        java.util.Collections.reverse(messages);
        return messages.stream().map(messageConverter::toDto).toList();
    }

    public void touchLastMessage(Long conversationId, String modelName) {
        AgentConversationInfo conversation = conversationService.getById(conversationId);
        if (conversation != null) {
            conversation.setLastMessageTime(LocalDateTime.now());
            if (modelName != null && !modelName.isBlank()) {
                conversation.setModelName(modelName);
            }
            conversationService.updateById(conversation);
        }
    }

    private String resolveModelNameForCreate(String requestModelName, Long projectId) {
        if (requestModelName != null && !requestModelName.isBlank()) {
            return requestModelName;
        }
        return modelResolver.resolveChatModel(projectId);
    }
}
