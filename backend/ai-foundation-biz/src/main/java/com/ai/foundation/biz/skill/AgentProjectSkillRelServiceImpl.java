package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentProjectSkillRel;
import com.ai.foundation.dal.mapper.AgentProjectSkillRelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentProjectSkillRelServiceImpl
        extends ServiceImpl<AgentProjectSkillRelMapper, AgentProjectSkillRel>
        implements AgentProjectSkillRelService {

    @Override
    public List<Long> listSkillIdsByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentProjectSkillRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AgentProjectSkillRel::getSkillId)
                .eq(AgentProjectSkillRel::getProjectId, projectId)
                .eq(AgentProjectSkillRel::getState, 1);
        return list(wrapper).stream()
                .map(AgentProjectSkillRel::getSkillId)
                .toList();
    }

    @Override
    public List<AgentProjectSkillRel> listByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentProjectSkillRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentProjectSkillRel::getProjectId, projectId)
                .orderByAsc(AgentProjectSkillRel::getId);
        return list(wrapper);
    }

    @Override
    public void removeByProjectIdAndSkillIds(Long projectId, List<Long> skillIds) {
        if (projectId == null || skillIds == null || skillIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<AgentProjectSkillRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentProjectSkillRel::getProjectId, projectId)
                .in(AgentProjectSkillRel::getSkillId, skillIds);
        remove(wrapper);
    }

    @Override
    public long countBySkillId(Long skillId) {
        if (skillId == null) {
            return 0;
        }
        LambdaQueryWrapper<AgentProjectSkillRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentProjectSkillRel::getSkillId, skillId);
        return count(wrapper);
    }
}
