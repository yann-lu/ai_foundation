package com.ai.foundation.facade.dto.skill;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class AgentSkillSaveRequest {

    private Long id;

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 128, message = "技能名称不能超过128字符")
    private String skillName;

    @NotBlank(message = "技能编码不能为空")
    @Size(max = 128, message = "技能编码不能超过128字符")
    private String skillCode;

    @Size(max = 2048, message = "描述不能超过2048字符")
    private String description;

    private String skillType = "PROMPT";

    private String systemPrompt;

    @Size(max = 4000, message = "运行时上下文模板不能超过4000字符")
    private String runtimeContextTemplate;

    private String configJson;

    private Integer state = 1;

    private List<Long> cliIds;
}
