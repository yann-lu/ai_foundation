package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentProjectSkillRel;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentProjectSkillRelService extends IService<AgentProjectSkillRel> {

    List<Long> listSkillIdsByProjectId(Long projectId);

    List<AgentProjectSkillRel> listByProjectId(Long projectId);

    void removeByProjectIdAndSkillIds(Long projectId, List<Long> skillIds);

    long countBySkillId(Long skillId);
}
