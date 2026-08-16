package com.ai.foundation.facade.dto.skill;

import lombok.Data;

@Data
public class SkillBindOptionDTO {

    private Long id;

    private String skillName;

    private String skillType;

    private String description;

    private Boolean bound;
}
