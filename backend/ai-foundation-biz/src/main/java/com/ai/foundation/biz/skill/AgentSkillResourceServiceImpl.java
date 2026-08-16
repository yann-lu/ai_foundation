package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentSkillResource;
import com.ai.foundation.dal.mapper.AgentSkillResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentSkillResourceServiceImpl
        extends ServiceImpl<AgentSkillResourceMapper, AgentSkillResource>
        implements AgentSkillResourceService {

    @Override
    public List<AgentSkillResource> listBySkillId(Long skillId) {
        if (skillId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentSkillResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSkillResource::getSkillId, skillId)
                .orderByAsc(AgentSkillResource::getSortOrder);
        return list(wrapper);
    }

    @Override
    public List<AgentSkillResource> listBySkillIdAndType(Long skillId, String resourceType) {
        if (skillId == null || resourceType == null || resourceType.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<AgentSkillResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSkillResource::getSkillId, skillId)
                .eq(AgentSkillResource::getResourceType, resourceType)
                .orderByAsc(AgentSkillResource::getSortOrder);
        return list(wrapper);
    }

    @Override
    public void removeBySkillId(Long skillId) {
        if (skillId == null) {
            return;
        }
        LambdaQueryWrapper<AgentSkillResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSkillResource::getSkillId, skillId);
        remove(wrapper);
    }

    @Override
    public long countByResourceTypeAndResourceId(String resourceType, Long resourceId) {
        if (resourceType == null || resourceType.isBlank() || resourceId == null) {
            return 0;
        }
        return this.count(new LambdaQueryWrapper<AgentSkillResource>()
                .eq(AgentSkillResource::getResourceType, resourceType)
                .eq(AgentSkillResource::getResourceId, resourceId));
    }

    @Override
    public List<Long> listResourceIdsBySkillIdAndType(Long skillId, String resourceType) {
        return listBySkillIdAndType(skillId, resourceType).stream()
                .map(AgentSkillResource::getResourceId)
                .toList();
    }
}
