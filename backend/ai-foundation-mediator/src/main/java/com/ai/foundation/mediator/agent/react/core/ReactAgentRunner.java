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

    private static final String DEFAULT_REACT_SYSTEM_PROMPT = """
            你是一个专业的 AI 助手，能够通过调用工具来帮助用户解决问题。
            
            工作方式：
            1. 理解用户的需求
            2. 如需外部信息，调用合适的工具
            3. 根据工具返回结果继续思考或给出最终答案
            4. 可以进行多轮工具调用直到完成任务
            
            注意事项：
            - 严格按照工具定义的参数格式调用
            - 调用工具时参数名要准确
            - 如果工具返回错误，尝试修正参数或换一个工具
            - 最终回答要简洁、准确、有帮助
            """;

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

    public Flux<RunStreamEnvelope> streamReactRun(AgentRunInfo run, AgentConversationInfo conversation,
                                                   String userMessage, String systemPrompt,
                                                   Sinks.Empty<Void> cancelSignal) {
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
        StringBuilder sb = new StringBuilder();

        // 项目身份设定优先（有项目提示词时作为身份开头）
        if (projectId != null) {
            AgentProject project = projectService.getById(projectId);
            if (project != null && StringUtils.isNotBlank(project.getSystemPrompt())) {
                sb.append(project.getSystemPrompt().trim()).append("\n\n");
            }
        }

        // 技能提示词（按顺序追加在项目提示词之后）
        if (projectId != null) {
            List<AgentSkillDefinition> skills = loadProjectSkills(projectId);
            if (skills != null && !skills.isEmpty()) {
                for (AgentSkillDefinition skill : skills) {
                    if (StringUtils.isNotBlank(skill.getSystemPrompt())) {
                        sb.append("【技能：").append(skill.getSkillName()).append("】\n");
                        sb.append(skill.getSystemPrompt().trim()).append("\n\n");
                    }
                }
            }
        }

        // ReAct 工作方式说明（工具调用规则）
        sb.append(DEFAULT_REACT_SYSTEM_PROMPT);

        if (StringUtils.isNotBlank(userSystemPrompt)) {
            sb.append("\n\n【附加指令】\n").append(userSystemPrompt.trim());
        }

        if (cliList != null && !cliList.isEmpty()) {
            sb.append("\n\n【可用工具】\n");
            sb.append("当前已挂载 ").append(cliList.size()).append(" 个 CLI 工具，");
            sb.append("工具名均以 react_cli_ 开头，需要时直接调用。");
        }

        return sb.toString();
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
