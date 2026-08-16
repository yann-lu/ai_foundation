package com.ai.foundation.facade.dto.skill;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BindSkillsRequest {

    @NotNull(message = "项目ID不能为空")
    private Long id;

    private List<Long> skillIds;
}
