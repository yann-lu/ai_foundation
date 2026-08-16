package com.ai.foundation.biz.mcp;

import com.ai.foundation.dal.entity.AgentMcpServer;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentMcpServerService extends IService<AgentMcpServer> {

    AgentMcpServer getByServerCode(String serverCode);

    AgentMcpServer getByIdValid(Long id);

    IPage<AgentMcpServer> page(Page<AgentMcpServer> page, String keyword,
                                String transportType, Integer state);

    boolean existsByServerCode(String serverCode, Long excludeId);
}
