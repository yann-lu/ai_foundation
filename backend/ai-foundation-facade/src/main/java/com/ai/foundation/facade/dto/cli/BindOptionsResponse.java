package com.ai.foundation.facade.dto.cli;

import lombok.Data;

import java.util.List;

@Data
public class BindOptionsResponse {

    private List<BindCapabilityOptionDTO> cliOptions;
}
