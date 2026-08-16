package com.ai.foundation.facade.dto.skill;

import lombok.Data;

@Data
public class AgentSkillPageRequest {

    private Integer current = 1;

    private Integer size = 10;

    private String keyword;

    private String skillType;

    private Integer state;
}
