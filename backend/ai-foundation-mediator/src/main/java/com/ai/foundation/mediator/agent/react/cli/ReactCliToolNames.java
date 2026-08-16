package com.ai.foundation.mediator.agent.react.cli;

import com.ai.foundation.dal.entity.AgentCliCommand;
import org.apache.commons.lang3.StringUtils;

public final class ReactCliToolNames {

    public static final String TOOL_PREFIX = "react_cli_";

    private ReactCliToolNames() {
    }

    public static String resolveToolName(AgentCliCommand cli) {
        if (cli == null || StringUtils.isBlank(cli.getCommandName())) {
            return "react_cli_unknown";
        }
        return TOOL_PREFIX + cli.getCommandName();
    }

    public static String resolveToolName(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return "react_cli_unknown";
        }
        return TOOL_PREFIX + commandName;
    }
}
