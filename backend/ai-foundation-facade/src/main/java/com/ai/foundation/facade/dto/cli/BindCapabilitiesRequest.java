package com.ai.foundation.facade.dto.cli;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BindCapabilitiesRequest {

    @NotNull(message = "项目ID不能为空")
    private Long id;

    private List<Long> cliIds;
}
