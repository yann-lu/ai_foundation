package com.ai.foundation.mediator.mcp;

import com.ai.foundation.biz.cli.AgentToolDefinitionService;
import com.ai.foundation.biz.converter.McpServerConverter;
import com.ai.foundation.biz.mcp.AgentMcpServerService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentMcpServer;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerDTO;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerPageRequest;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerSaveRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMcpServerMedServiceImpl implements AgentMcpServerMedService {

    private final AgentMcpServerService mcpServerService;
    private final AgentToolDefinitionService toolDefinitionService;
    private final McpServerConverter mcpServerConverter;
    private final McpClientPool mcpClientPool;

    @Override
    public PageResult<AgentMcpServerDTO> page(AgentMcpServerPageRequest request) {
        Page<AgentMcpServer> page = new Page<>(request.getCurrent(), request.getSize());
        var result = mcpServerService.page(page, request.getKeyword(),
                request.getTransportType(), request.getState());
        List<AgentMcpServerDTO> records = result.getRecords().stream()
                .map(mcpServerConverter::toDto)
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AgentMcpServerDTO detail(Long id) {
        AgentMcpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "MCP 服务器不存在");
        }
        return mcpServerConverter.toDto(server);
    }

    @Override
    public void save(AgentMcpServerSaveRequest request, String operator) {
        if (mcpServerService.existsByServerCode(request.getServerCode(), request.getId())) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "服务器编码已存在");
        }
        validateTransport(request);

        AgentMcpServer server;
        if (request.getId() == null) {
            server = mcpServerConverter.toEntity(request);
            if (server.getState() == null) {
                server.setState(CommonConstants.STATE_ENABLED);
            }
            server.setCreateUser(operator);
            server.setModifyUser(operator);
            mcpServerService.save(server);
            log.info("创建 MCP 服务器成功 code={} operator={}", request.getServerCode(), operator);
        } else {
            AgentMcpServer existing = mcpServerService.getById(request.getId());
            if (existing == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "MCP 服务器不存在");
            }
            mcpServerConverter.updateEntity(request, existing);
            existing.setModifyUser(operator);
            mcpServerService.updateById(existing);
            // 配置变更后失效连接池中的旧 client，下次调用会按新配置重建
            mcpClientPool.invalidate(existing.getId());
            log.info("更新 MCP 服务器成功 id={} operator={}", request.getId(), operator);
        }
    }

    @Override
    public void delete(Long id, String operator) {
        AgentMcpServer server = mcpServerService.getById(id);
        if (server == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "MCP 服务器不存在");
        }
        long toolCount = toolDefinitionService.countByMcpServerId(id);
        if (toolCount > 0) {
            throw new BusinessException(ResultCode.STATE_INVALID,
                    "该 MCP 服务器已被 " + toolCount + " 个工具引用，请先删除对应工具");
        }
        mcpClientPool.invalidate(id);
        mcpServerService.removeById(id);
        log.info("删除 MCP 服务器 id={} operator={}", id, operator);
    }

    private void validateTransport(AgentMcpServerSaveRequest request) {
        String transport = StringUtils.defaultIfBlank(request.getTransportType(), "stdio");
        if ("stdio".equalsIgnoreCase(transport)) {
            if (StringUtils.isBlank(request.getCommand())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "stdio 方式下启动命令不能为空");
            }
        } else if ("sse".equalsIgnoreCase(transport) || "http".equalsIgnoreCase(transport)) {
            if (StringUtils.isBlank(request.getBaseUrl())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "sse/http 方式下 baseUrl 不能为空");
            }
        } else {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "不支持的传输方式: " + request.getTransportType());
        }
    }
}
