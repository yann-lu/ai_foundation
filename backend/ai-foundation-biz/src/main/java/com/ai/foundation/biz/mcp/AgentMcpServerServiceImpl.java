package com.ai.foundation.biz.mcp;

import com.ai.foundation.dal.entity.AgentMcpServer;
import com.ai.foundation.dal.mapper.AgentMcpServerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;

@Service
public class AgentMcpServerServiceImpl extends ServiceImpl<AgentMcpServerMapper, AgentMcpServer>
        implements AgentMcpServerService {

    @Override
    public AgentMcpServer getByServerCode(String serverCode) {
        if (serverCode == null || serverCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<AgentMcpServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcpServer::getServerCode, serverCode)
                .eq(AgentMcpServer::getIsDelete, 0);
        return getOne(wrapper, false);
    }

    @Override
    public AgentMcpServer getByIdValid(Long id) {
        if (id == null) {
            return null;
        }
        AgentMcpServer server = getById(id);
        if (server == null || server.getIsDelete() != null && server.getIsDelete() == 1) {
            return null;
        }
        return server;
    }

    @Override
    public IPage<AgentMcpServer> page(Page<AgentMcpServer> page, String keyword,
                                       String transportType, Integer state) {
        LambdaQueryWrapper<AgentMcpServer> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AgentMcpServer::getServerName, keyword)
                    .or().like(AgentMcpServer::getServerCode, keyword));
        }
        if (transportType != null && !transportType.isBlank()) {
            wrapper.eq(AgentMcpServer::getTransportType, transportType);
        }
        if (state != null) {
            wrapper.eq(AgentMcpServer::getState, state);
        }
        wrapper.orderByDesc(AgentMcpServer::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public boolean existsByServerCode(String serverCode, Long excludeId) {
        if (serverCode == null || serverCode.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<AgentMcpServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcpServer::getServerCode, serverCode);
        if (excludeId != null) {
            wrapper.ne(AgentMcpServer::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }
}
