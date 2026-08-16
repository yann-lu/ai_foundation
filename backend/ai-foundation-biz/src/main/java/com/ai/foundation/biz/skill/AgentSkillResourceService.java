package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentSkillResource;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentSkillResourceService extends IService<AgentSkillResource> {

    List<AgentSkillResource> listBySkillId(Long skillId);

    List<AgentSkillResource> listBySkillIdAndType(Long skillId, String resourceType);

    void removeBySkillId(Long skillId);

    List<Long> listResourceIdsBySkillIdAndType(Long skillId, String resourceType);

    long countByResourceTypeAndResourceId(String resourceType, Long resourceId);
}
