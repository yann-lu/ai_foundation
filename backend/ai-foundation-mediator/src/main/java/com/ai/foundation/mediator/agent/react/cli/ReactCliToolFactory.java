package com.ai.foundation.mediator.agent.react.cli;

import com.ai.foundation.biz.cli.AgentCliParamService;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentCliParam;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.ai.foundation.mediator.agent.react.core.ReactRunSession;
import com.ai.foundation.mediator.agent.react.dto.ReactCliToolInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactCliToolFactory {

    private static final int MAX_DESC_LEN = 2000;

    private final ReactCliToolInvoker reactCliToolInvoker;
    private final AgentCliParamService cliParamService;

    public List<ToolCallback> buildCliTools(List<AgentCliCommand> cliList, ReactRunSession runSession) {
        if (cliList == null || cliList.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> tools = new ArrayList<>();
        for (AgentCliCommand cli : cliList) {
            if (cli == null || cli.getId() == null) {
                continue;
            }
            ToolCallback tool = buildToolCallback(cli, runSession);
            if (tool != null) {
                tools.add(tool);
            }
        }
        log.info("ReactCliToolFactory built {} tool(s) from {} CLI(s)", tools.size(), cliList.size());
        return tools;
    }

    private ToolCallback buildToolCallback(AgentCliCommand cli, ReactRunSession runSession) {
        String toolName = ReactCliToolNames.resolveToolName(cli);
        List<AgentCliParam> params = cliParamService.listByCliId(cli.getId());
        String description = buildDescription(cli, params);
        String inputSchema = ReactCliToolSchemaBuilder.buildInputSchema(params);

        return FunctionToolCallback.builder(toolName,
                        (ReactCliToolInput input) -> ReactRunSession.callWithSession(runSession, () ->
                                reactCliToolInvoker.invoke(cli, input)))
                .description(description)
                .inputSchema(inputSchema)
                .inputType(ReactCliToolInput.class)
                .build();
    }

    private String buildDescription(AgentCliCommand cli, List<AgentCliParam> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("【CLI能力】");
        sb.append(StringUtils.defaultIfBlank(cli.getDescription(),
                StringUtils.defaultIfBlank(cli.getCommandName(), "未命名能力")));
        sb.append("（命令标识：").append(ReactCliToolNames.resolveToolName(cli)).append("）");
        sb.append("。参数说明：");
        sb.append(ReactCliToolSchemaBuilder.buildParamDescription(params));
        sb.append(" ").append(ReactCliToolSchemaBuilder.buildDirectInvokeHint(params));
        sb.append("调用后将请求后端 API 并返回结果。");

        String desc = sb.toString();
        if (desc.length() > MAX_DESC_LEN) {
            desc = desc.substring(0, MAX_DESC_LEN) + "…";
        }
        return desc;
    }
}
