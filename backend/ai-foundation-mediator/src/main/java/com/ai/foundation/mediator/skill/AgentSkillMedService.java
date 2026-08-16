package com.ai.foundation.mediator.skill;

import com.ai.foundation.facade.dto.skill.AgentSkillDTO;
import com.ai.foundation.facade.dto.skill.AgentSkillPageRequest;
import com.ai.foundation.facade.dto.skill.AgentSkillSaveRequest;
import com.ai.foundation.facade.dto.skill.SkillBindOptionDTO;
import com.ai.foundation.com.response.PageResult;

import java.util.List;

public interface AgentSkillMedService {

    PageResult<AgentSkillDTO> page(AgentSkillPageRequest request);

    AgentSkillDTO detail(Long id);

    void save(AgentSkillSaveRequest request, String operator);

    void delete(Long id, String operator);

    List<SkillBindOptionDTO> listBindOptions(Long projectId);
}
