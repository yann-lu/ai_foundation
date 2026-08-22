package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 模拟一个真实业务场景，输出完整的 ReAct System Prompt。
 * 假设：星辰旅游助手项目，挂 1 个 Skill + 2 个 CLI 工具 + 用户临时附了一段指令。
 * <p>
 * A 档后：不再有 projectSystemPrompt 段，baseRole 由 projectName 驱动。
 */
public class ReactSystemPromptFullSampleTest {

    public static void main(String[] args) throws Exception {
        ReactSystemPromptComposer composer = new ReactSystemPromptComposer();

        AgentSkillDefinition skill = new AgentSkillDefinition();
        skill.setSkillCode("hotel_order_query");
        skill.setSkillName("酒店订单查询");
        skill.setDescription("支持订单检索、详情、状态变更");
        skill.setSystemPrompt("1. 必须先获取订单号或客户手机号后四位作为入参\n2. 详情返回禁止截断金额字段\n3. 异常订单优先走人工确认");
        skill.setState(1);

        AgentCliCommand cli1 = new AgentCliCommand();
        cli1.setCommandName("epms_order_list");
        cli1.setCommandPrefix("epms");
        cli1.setCommandType("API");
        cli1.setState(1);

        AgentCliCommand cli2 = new AgentCliCommand();
        cli2.setCommandName("epms_order_detail");
        cli2.setCommandPrefix("epms");
        cli2.setCommandType("API");
        cli2.setState(1);

        // 4-arg 签名：compose(skills, cliList, userSystemPrompt, projectName)
        Method compose = ReactSystemPromptComposer.class.getMethod(
                "compose", List.class, List.class, String.class, String.class);
        Object out = compose.invoke(composer,
                List.of(skill),
                List.of(cli1, cli2),
                "本轮所有金额展示保留两位小数。",
                "星辰旅游助手");
        System.out.println(out);
    }
}
