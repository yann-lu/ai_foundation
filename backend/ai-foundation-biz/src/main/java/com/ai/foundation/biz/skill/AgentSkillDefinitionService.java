package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentSkillDefinitionService extends IService<AgentSkillDefinition> {

    IPage<AgentSkillDefinition> page(Page<AgentSkillDefinition> page, String keyword,
                                      String skillType, Integer state);

    boolean existsBySkillCode(String skillCode, Long excludeId);

    List<AgentSkillDefinition> listByIds(List<Long> ids);
}
