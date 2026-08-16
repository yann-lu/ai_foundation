package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentProjectCliMapping;
import com.ai.foundation.dal.mapper.AgentProjectCliMappingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentProjectCliMappingServiceImpl
        extends ServiceImpl<AgentProjectCliMappingMapper, AgentProjectCliMapping>
        implements AgentProjectCliMappingService {

    @Override
    public List<AgentProjectCliMapping> listByProjectId(Long projectId) {
        return this.list(new LambdaQueryWrapper<AgentProjectCliMapping>()
                .eq(AgentProjectCliMapping::getProjectId, projectId));
    }

    @Override
    public List<Long> listCliIdsByProjectId(Long projectId) {
        return listByProjectId(projectId).stream()
                .map(AgentProjectCliMapping::getCliId)
                .toList();
    }

    @Override
    public void removeByProjectId(Long projectId) {
        this.remove(new LambdaQueryWrapper<AgentProjectCliMapping>()
                .eq(AgentProjectCliMapping::getProjectId, projectId));
    }

    @Override
    public void removeByProjectIdAndCliIds(Long projectId, List<Long> cliIds) {
        if (cliIds == null || cliIds.isEmpty()) {
            return;
        }
        baseMapper.deleteByProjectIdAndCliIdsPhysical(projectId, cliIds);
    }

    @Override
    public void removeByCliId(Long cliId) {
        baseMapper.deleteByCliIdPhysical(cliId);
    }

    @Override
    public int countByCliId(Long cliId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<AgentProjectCliMapping>()
                .eq(AgentProjectCliMapping::getCliId, cliId)));
    }

    @Override
    public List<Long> listProjectIdsByCliId(Long cliId) {
        return this.list(new LambdaQueryWrapper<AgentProjectCliMapping>()
                        .eq(AgentProjectCliMapping::getCliId, cliId))
                .stream()
                .map(AgentProjectCliMapping::getProjectId)
                .toList();
    }
}
