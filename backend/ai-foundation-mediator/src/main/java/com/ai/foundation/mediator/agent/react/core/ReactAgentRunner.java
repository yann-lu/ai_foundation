package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.biz.cli.AgentCliCommandService;
import com.ai.foundation.biz.cli.AgentProjectCliMappingService;
import com.ai.foundation.biz.skill.AgentProjectSkillRelService;
import com.ai.foundation.biz.skill.AgentSkillDefinitionService;
import com.ai.foundation.biz.skill.AgentSkillResourceService;
import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.biz.run.AgentRunInfoService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.dal.entity.AgentRunInfo;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;
import com.ai.foundation.mediator.agent.react.cli.ReactCliToolFactory;
import com.ai.foundation.mediator.agent.react.skill.ReactSystemPromptComposer;
import com.ai.foundation.mediator.chat.AiChatMedService;
import com.ai.foundation.mediator.chat.ChatHistoryComposer;
import com.ai.foundation.mediator.model.AgentModelResolver;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactAgentRunner {


    private final ChatModel chatModel;
    private final AgentModelResolver modelResolver;
    private final ReactCliToolFactory reactCliToolFactory;
    private final AgentCliCommandService cliCommandService;
    private final AgentProjectCliMappingService projectCliMappingService;
    private final AgentProjectService projectService;
    private final AgentRunInfoService runInfoService;
    private final AgentProjectSkillRelService projectSkillRelService;
    private final AgentSkillDefinitionService skillDefinitionService;
    private final AgentSkillResourceService skillResourceService;
    private final AiChatMedService aiChatMedService;
    private final ChatHistoryComposer chatHistoryComposer;
    private final ReactSystemPromptComposer systemPromptComposer;

    public Flux<RunStreamEnvelope> streamReactRun(AgentRunInfo run, AgentConversationInfo conversation,
                                                   String userMessage, String systemPrompt,
                                                   Sinks.Empty<Void> cancelSignal) {
        long startTime = System.currentTimeMillis();
        String runCode = run.getRunCode();
        String conversationCode = conversation.getConversationCode();
        Long projectId = conversation.getProjectId();
        String modelName = modelResolver.resolveChatModel(projectId);

        ReactRunSession session = new ReactRunSession();
        session.setRunId(run.getId());
        session.setRunCode(runCode);
        session.setConversationCode(conversationCode);
        session.setUserMessage(userMessage);
        session.setModelName(modelName);
        session.setProjectId(projectId);
        session.setExecutionContext(buildExecutionContext(projectId));

        List<AgentCliCommand> cliList = loadProjectCliCommands(projectId);
        session.setAvailableCliCommands(cliList);

        List<ToolCallback> tools = reactCliToolFactory.buildCliTools(cliList, session);

        String finalSystemPrompt = buildFinalSystemPrompt(projectId, systemPrompt, cliList);

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userMessage));

        ChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .streamUsage(true)
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("foundation_react_agent")
                .model(chatModel)
                .chatOptions(chatOptions)
                .tools(tools)
                .systemPrompt(finalSystemPrompt)
                .build();

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(conversationCode)
                .build();

        ReactStreamHandler streamHandler = new ReactStreamHandler(runCode, conversationCode);

        Flux<RunStreamEnvelope> startEvents = Flux.just(
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.RUN_START,
                        RunStateEnum.EXECUTING.getCode(), null),
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.CHAT_START,
                        RunStateEnum.EXECUTING.getCode(), null),
                envelope(runCode, conversationCode, RunStreamEventTypeEnum.USER_MESSAGE,
                        RunStateEnum.EXECUTING.getCode(), userMessage)
        );

        Flux<RunStreamEnvelope> reactEvents = Flux.defer(() -> {
            ReactRunSession.set(session);
            try {
                Flux<NodeOutput> nodeFlux = agent.stream(messages, runnableConfig);
                return nodeFlux
                        .concatMap(nodeOutput -> Flux.fromIterable(streamHandler.handle(nodeOutput)))
                        .concatWith(Mono.fromCallable(() -> {
                            String reply = streamHandler.getFinalReply();
                            try {
                                saveRunResult(run, reply, streamHandler.getReasoning());
                                updateRunState(run, RunStateEnum.COMPLETED, 1);
                                if (StringUtils.isNotBlank(reply)) {
                                    long duration = System.currentTimeMillis() - startTime;
                                    aiChatMedService.saveAssistantMessage(conversation, reply, duration);
                                    chatHistoryComposer.completeTurn(conversation.getId(),
                                            userMessage, reply, conversation.getModelName());
                                }
                            } catch (Exception ex) {
                                log.warn("保存 Run 结果失败 runCode={}", runCode, ex);
                            }
                            return envelope(runCode, conversationCode,
                                    RunStreamEventTypeEnum.RUN_COMPLETE, RunStateEnum.COMPLETED.getCode(), reply);
                        }).subscribeOn(Schedulers.boundedElastic()))
                        .doOnError(ex -> {
                            log.error("ReactAgentRunner stream failed, runCode={}", runCode, ex);
                            updateRunState(run, RunStateEnum.FAILED, 2);
                        });
            } catch (Exception ex) {
                log.error("ReactAgentRunner failed to start stream, runCode={}", runCode, ex);
                updateRunState(run, RunStateEnum.FAILED, 2);
                return Flux.just(envelope(runCode, conversationCode,
                        RunStreamEventTypeEnum.RUN_ERROR, RunStateEnum.FAILED.getCode(),
                        ex.getMessage() != null ? ex.getMessage() : "ReAct 启动失败"));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doFinally(signal -> {
            ReactRunSession.clear();
        });

        return Flux.concat(startEvents, reactEvents)
                .takeUntil(e -> isTerminal(e.getEventType()));
    }

    private String buildFinalSystemPrompt(Long projectId, String userSystemPrompt,
                                           List<AgentCliCommand> cliList) {
        String projectSystemPrompt = loadProjectSystemPrompt(projectId);
        List<AgentSkillDefinition> skills = loadProjectSkills(projectId);
        return systemPromptComposer.compose(projectSystemPrompt, skills, cliList, userSystemPrompt);
    }

    private String loadProjectSystemPrompt(Long projectId) {
        if (projectId == null) {
            return null;
        }
        try {
            AgentProject project = projectService.getById(projectId);
            return project == null ? null : project.getSystemPrompt();
        } catch (Exception ex) {
            log.warn("加载项目系统提示词失败, projectId={}", projectId, ex);
            return null;
        }
    }

    private List<AgentSkillDefinition> loadProjectSkills(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        try {
            List<Long> skillIds = projectSkillRelService.listSkillIdsByProjectId(projectId);
            if (skillIds == null || skillIds.isEmpty()) {
                return List.of();
            }
            return skillDefinitionService.lambdaQuery()
                    .in(AgentSkillDefinition::getId, skillIds)
                    .eq(AgentSkillDefinition::getState, 1)
                    .list();
        } catch (Exception ex) {
            log.warn("加载项目技能列表失败, projectId={}", projectId, ex);
            return List.of();
        }
    }

    private List<AgentCliCommand> loadProjectCliCommands(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        try {
            java.util.Set<Long> allCliIds = new java.util.HashSet<>(projectCliMappingService.listCliIdsByProjectId(projectId));

            List<Long> skillIds = projectSkillRelService.listSkillIdsByProjectId(projectId);
            if (skillIds != null && !skillIds.isEmpty()) {
                for (Long skillId : skillIds) {
                    List<Long> skillCliIds = skillResourceService.listResourceIdsBySkillIdAndType(skillId, "CLI");
                    if (skillCliIds != null) {
                        allCliIds.addAll(skillCliIds);
                    }
                }
            }

            if (allCliIds.isEmpty()) {
                log.info("loadProjectCliCommands empty, projectId={}", projectId);
                return List.of();
            }
            List<AgentCliCommand> result = cliCommandService.lambdaQuery()
                    .in(AgentCliCommand::getId, allCliIds)
                    .eq(AgentCliCommand::getState, 1)
                    .in(AgentCliCommand::getCommandType, "API", "MCP")
                    .list();
            log.info("loadProjectCliCommands projectId={} total={} loaded={} names={}",
                    projectId, allCliIds.size(), result.size(),
                    result.stream().map(AgentCliCommand::getCommandName).toList());
            return result;
        } catch (Exception ex) {
            log.warn("加载项目 CLI 列表失败, projectId={}", projectId, ex);
            return List.of();
        }
    }

    private AgentExecutionContext buildExecutionContext(Long projectId) {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setProjectId(projectId);
        return context;
    }

    private void saveRunResult(AgentRunInfo run, String reply, String reasoning) {
        AgentRunInfo update = new AgentRunInfo();
        update.setId(run.getId());
        update.setReply(StringUtils.defaultString(reply));
        update.setReasoning(StringUtils.defaultString(reasoning));
        update.setRunType(RunTypeConstant.REACT);
        runInfoService.updateById(update);
    }

    private void updateRunState(AgentRunInfo run, RunStateEnum state, int compatState) {
        AgentRunInfo update = new AgentRunInfo();
        update.setId(run.getId());
        update.setRunType(RunTypeConstant.REACT);
        update.setTaskState(state.getCode());
        update.setState(compatState);
        update.setUpdateTime(LocalDateTime.now());
        runInfoService.updateById(update);
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

    private boolean isTerminal(String eventType) {
        return RunStreamEventTypeEnum.RUN_COMPLETE.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_ERROR.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_CANCELLED.getCode().equals(eventType);
    }
}
