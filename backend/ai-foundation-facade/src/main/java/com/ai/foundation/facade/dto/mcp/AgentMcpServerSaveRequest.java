package com.ai.foundation.facade.dto.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMcpServerSaveRequest {

    private Long id;

    @NotBlank(message = "服务器编码不能为空")
    @Size(max = 64, message = "服务器编码不能超过64字符")
    private String serverCode;

    @NotBlank(message = "服务器名称不能为空")
    @Size(max = 128, message = "服务器名称不能超过128字符")
    private String serverName;

    @Size(max = 1024, message = "描述不能超过1024字符")
    private String description;

    /**
     * 传输方式：stdio / sse / http，当前仅支持 stdio
     */
    @NotBlank(message = "传输方式不能为空")
    private String transportType = "stdio";

    /**
     * stdio 启动命令，如：npx -y bing-cn-mcp-enhanced
     */
    @Size(max = 512, message = "启动命令不能超过512字符")
    private String command;

    @Size(max = 512, message = "工作目录不能超过512字符")
    private String workingDir;

    /**
     * 环境变量 JSON 字符串，如 {"KEY":"value"}
     */
    private String envVars;

    /**
     * sse/http 方式下的 baseUrl
     */
    @Size(max = 512, message = "Base URL 不能超过512字符")
    private String baseUrl;

    private String authType = "NONE";

    private String authConfig;

    private Integer state = 1;
}
