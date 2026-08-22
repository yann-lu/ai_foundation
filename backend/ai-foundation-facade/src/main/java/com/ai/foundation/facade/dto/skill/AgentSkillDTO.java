package com.ai.foundation.facade.dto.skill;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentSkillDTO {

    private Long id;

    private String skillName;

    private String skillCode;

    private String description;

    private String skillType;

    private String systemPrompt;

    private String runtimeContextTemplate;

    private String configJson;

    private Integer state;

    private List<Long> cliIds;

    private String createUser;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
