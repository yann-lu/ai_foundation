package com.ai.foundation.mediator.conversation;

import com.ai.foundation.biz.converter.ConversationConverter;
import com.ai.foundation.biz.converter.MessageConverter;
import com.ai.foundation.biz.conversation.AgentConversationService;
import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.project.AgentProjectService;
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
import com.ai.foundation.mediator.prompt.ProjectPromptService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationMedService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;
    private final AgentProjectService projectService;
    private final AgentModelResolver modelResolver;
    private final ProjectPromptService projectPromptService;
    private final ConversationConverter conversationConverter;
    private final MessageConverter messageConverter;

    public ConversationDTO create(ConversationCreateRequest request) {
        AgentProject project = projectService.getByCode(request.getProductCode());
        if (project == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品编码对应的项目不存在: " + request.getProductCode());
        }

        AgentConversationInfo entity = new AgentConversationInfo();
        entity.setProjectId(project.getId());
        entity.setProductCode(request.getProductCode());
        entity.setConversationCode(ConversationCodeGenerator.generate());
        entity.setUserId(request.getUserId() != null ? request.getUserId() : 0L);
        entity.setContextVariables(projectPromptService.buildConversationVariables(project, request));
        entity.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() : "新会话");
        entity.setModelProvider("openai");
        entity.setModelName(resolveModelNameForCreate(request.getModelName(), project.getId()));
        entity.setIsPin(0);
        entity.setState(0);
        conversationService.save(entity);
        log.info("创建会话成功 code={} projectId={} model={}", entity.getConversationCode(), entity.getProjectId(), entity.getModelName());
        return conversationConverter.toDto(entity);
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
