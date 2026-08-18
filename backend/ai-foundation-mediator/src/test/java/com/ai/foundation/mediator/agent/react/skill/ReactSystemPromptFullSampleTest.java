package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模拟一个真实业务场景，输出完整的 ReAct System Prompt。
 * 假设：酒店订单管理项目，挂 1 个 Skill + 2 个 CLI 工具 + 用户临时附了一段指令。
 */
public class ReactSystemPromptFullSampleTest {

    public static void main(String[] args) throws Exception {
        SkillCapabilityRegistry registry = new SkillCapabilityRegistry(Collections.emptyList());
        ReactSystemPromptComposer composer = new ReactSystemPromptComposer(registry);

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

        Method compose = ReactSystemPromptComposer.class.getMethod(
                "compose", String.class, List.class, List.class, String.class);
        Object out = compose.invoke(composer,
                "你是【星辰酒店集团】的智能管家，负责协助前厅、客房、财务同事处理日常运营。\n"
                        + "回答必须专业、礼貌，且严格使用简体中文。",
                List.of(skill),
                List.of(cli1, cli2),
                "本轮所有金额展示保留两位小数。");
        System.out.println(out);
    }
}
