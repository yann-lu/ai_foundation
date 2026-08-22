package com.ai.foundation.mediator.agent.react.skill;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * {@code skill_{code}} 工具入参：可选 instruction 覆盖默认用户指令。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactSkillToolInput {

    private String instruction;
}
