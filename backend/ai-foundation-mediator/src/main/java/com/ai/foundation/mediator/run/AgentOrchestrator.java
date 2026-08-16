package com.ai.foundation.mediator.run;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.run.AgentRunInfoService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.mediator.chat.AiChatMedService;
import com.ai.foundation.mediator.chat.ChatHistoryComposer;
import com.ai.foundation.mediator.chat.ChatStreamChunk;
import com.ai.foundation.mediator.chat.ChatRequestMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AiChatMedService aiChatMedService;
    private final AgentRunInfoService runInfoService;
    private final AgentMessageService messageService;
    private final ChatHistoryComposer chatHistoryComposer;
    private final ObjectMapper objectMapper;

    /**
     * 响应式流式执行 Run，直接将模型 token 流通过 SSE 推送给客户端。
     *
     * <p>三段式 Flux.concat：
     * <ol>
     *   <li>start — RUN_START / CHAT_START / REQUEST_MESSAGES / USER_MESSAGE 事件</li>
     *   <li>tokens — 模型流式 token，逐条映射为 CHAT_REASONING / CHAT_TOKEN 事件</li>
     *   <li>end — 保存助手消息、更新热缓存、写回 Run 详情（消息栈/回复/思考）、更新 Run 状态，推送 CHAT_COMPLETE / RUN_COMPLETE</li>
     * </ol>
     *
     * @param run          已持久化的 Run
     * @param conversation 会话
     * @param userMessage  用户消息原文
     * @param systemPrompt 系统提示词（可选）
     * @return 事件流
     */
    public Flux<RunStreamEnvelope> streamRun(AgentRunInfo run, AgentConversationInfo conversation,
                                               String userMessage, String systemPrompt) {
        String runCode = run.getRunCode();
        String conversationCode = conversation.getConversationCode();
        long startTime = System.currentTimeMillis();
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        List<ChatRequestMessage> requestMessages = aiChatMedService
                .buildRequestMessages(conversation, userMessage, systemPrompt);

        Flux<RunStreamEnvelope> startEvents = Flux.just(
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.RUN_START,
                        RunStateEnum.EXECUTING.getCode(), null),
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.CHAT_START,
                        RunStateEnum.EXECUTING.getCode(), null),
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.REQUEST_MESSAGES,
                        RunStateEnum.EXECUTING.getCode(), requestMessages),
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.USER_MESSAGE,
                        RunStateEnum.EXECUTING.getCode(), userMessage)
        );

        Flux<RunStreamEnvelope> tokenEvents = aiChatMedService
                .streamChunks(conversation, userMessage, systemPrompt, null)
                .doOnNext(chunk -> {
                    if (chunk.hasContent()) {
                        contentBuilder.append(chunk.content());
                    }
                    if (chunk.hasReasoning()) {
                        reasoningBuilder.append(chunk.reasoning());
                    }
                })
                .flatMap(chunk -> {
                    List<RunStreamEnvelope> events = new ArrayList<>();
                    if (chunk.hasReasoning()) {
                        events.add(envelope(runCode, conversationCode,
                                RunStreamEventTypeEnum.CHAT_REASONING,
                                RunStateEnum.EXECUTING.getCode(), chunk.reasoning()));
                    }
                    if (chunk.hasContent()) {
                        events.add(envelope(runCode, conversationCode,
                                RunStreamEventTypeEnum.CHAT_TOKEN,
                                RunStateEnum.EXECUTING.getCode(), chunk.content()));
                    }
                    return Flux.fromIterable(events);
                });

       Flux<RunStreamEnvelope> endEvents = Mono
               .fromCallable(() -> {
                   String reply = contentBuilder.toString();
                   String reasoning = reasoningBuilder.toString();
                   long duration = System.currentTimeMillis() - startTime;
                   if (StringUtils.isNotBlank(reply)) {
                       aiChatMedService.saveAssistantMessage(conversation, reply, duration);
                       chatHistoryComposer.completeTurn(conversation.getId(),
                               userMessage, reply, conversation.getModelName());
                   }
                   saveRunDetails(run, requestMessages, reply, reasoning);
                   updateRunState(run, RunTypeConstant.CHAT, RunStateEnum.COMPLETED, 1);
                   log.info("Run 完成 runCode={} duration={}ms replyLength={}", runCode, duration,
                           reply == null ? 0 : reply.length());
                   return reply;
               })
               .subscribeOn(Schedulers.boundedElastic())
               .flatMapMany(reply -> {
                   List<RunStreamEnvelope> events = new ArrayList<>();
                   events.add(envelope(runCode, conversationCode, RunStreamEventTypeEnum.CHAT_COMPLETE,
                           RunStateEnum.COMPLETED.getCode(), null));
                   events.add(envelope(runCode, conversationCode, RunStreamEventTypeEnum.RUN_COMPLETE,
                           RunStateEnum.COMPLETED.getCode(), reply));
                  return Flux.fromIterable(events);
              });

        return Flux.concat(startEvents, tokenEvents, endEvents)
                .onErrorResume(err -> {
                    log.error("Run 执行失败 runCode={}", runCode, err);
                    return Mono.fromCallable(() -> {
                            updateRunState(run, RunTypeConstant.CHAT, RunStateEnum.FAILED, 2);
                            return err.getMessage() != null ? err.getMessage() : "执行失败";
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(msg -> Flux.just(
                                envelope(runCode, conversationCode, RunStreamEventTypeEnum.RUN_ERROR,
                                        RunStateEnum.FAILED.getCode(), msg)));
                });
    }

    /**
     * 取消 Run：更新状态。
     *
     * @param run              已持久化的 Run
     * @param conversationCode 会话编码
     */
    public void cancelRun(AgentRunInfo run, String conversationCode) {
        updateRunState(run, run.getRunType(), RunStateEnum.CANCELLED, 2);
        log.info("Run 已取消 runCode={}", run.getRunCode());
    }

    /**
     * 写回 Run 详情（消息栈 / 回复 / 思考链）到 agent_run 表，供刷新后 Inspector 还原。
     */
    private void saveRunDetails(AgentRunInfo run, List<ChatRequestMessage> requestMessages,
                                String reply, String reasoning) {
        try {
            AgentRunInfo update = new AgentRunInfo();
            update.setId(run.getId());
            update.setRequestMessages(objectMapper.writeValueAsString(requestMessages));
            update.setReply(StringUtils.defaultString(reply));
            update.setReasoning(StringUtils.defaultString(reasoning));
            runInfoService.updateById(update);
        } catch (JsonProcessingException e) {
            log.warn("序列化 requestMessages 失败 runCode={}", run.getRunCode(), e);
        } catch (Exception e) {
            log.warn("写回 Run 详情失败 runCode={}", run.getRunCode(), e);
        }
    }

    private void updateRunState(AgentRunInfo run, String runType, RunStateEnum state, int compatState) {
        AgentRunInfo update = new AgentRunInfo();
        update.setId(run.getId());
        update.setRunType(runType);
        update.setTaskState(state.getCode());
        update.setState(compatState);
        update.setUpdateTime(LocalDateTime.now());
        runInfoService.updateById(update);
        run.setRunType(runType);
        run.setTaskState(state.getCode());
        run.setState(compatState);
    }

    private RunStreamEnvelope envelope(String runCode, String conversationCode,
                                        RunStreamEventTypeEnum type, String taskState, Object data) {
        RunStreamEnvelope env = new RunStreamEnvelope();
        env.setEventType(type.getCode());
        env.setRunCode(runCode);
        env.setConversationCode(conversationCode);
        env.setTimestamp(System.currentTimeMillis());
        env.setTaskState(taskState);
        env.setData(data);
        return env;
    }
}
