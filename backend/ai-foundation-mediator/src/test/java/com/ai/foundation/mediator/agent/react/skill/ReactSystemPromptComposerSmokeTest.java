package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不引入 JUnit 的 smoke 测试：直接通过反射调用 composer 并打印各段关键词命中情况。
 * 验证步骤 1 验收标准：8 段关键词（安全红线 / 附件优先级 / 思考区 / 行动 / Skill 索引 / 硬规则 / 文件产物 / 最终回复）。
 */
public class ReactSystemPromptComposerSmokeTest {

    public static void main(String[] args) throws Exception {
        SkillCapabilityRegistry registry = new SkillCapabilityRegistry(Collections.emptyList());
        ReactSystemPromptComposer composer = new ReactSystemPromptComposer(registry);

        // 构造 skill（带 systemPrompt 以触发 Skill 详情渲染）
        AgentSkillDefinition skill = new AgentSkillDefinition();
        skill.setSkillCode("hotel_order_query");
        skill.setSkillName("酒店订单查询");
        skill.setDescription("支持订单检索、详情、状态变更");
        skill.setSystemPrompt("1. 先根据订单号或客户信息检索订单\n2. 返回订单详情时禁止编造金额");
        skill.setState(1);
        List<AgentSkillDefinition> skills = new ArrayList<>();
        skills.add(skill);

        // 构造 cli 列表（带 commandPrefix 以触发 CLI 命名提示）
        AgentCliCommand cli = new AgentCliCommand();
        cli.setId(1L);
        cli.setCommandName("epms_order_list");
        cli.setCommandPrefix("epms");
        cli.setCommandType("API");
        cli.setState(1);
        List<AgentCliCommand> cliList = new ArrayList<>();
        cliList.add(cli);

        // 反射调 compose（避免 import 私有包）
        Method compose = ReactSystemPromptComposer.class.getMethod(
                "compose", String.class, List.class, List.class, String.class);
        Object out = compose.invoke(composer, "你是酒店管家", skills, cliList, "测试附加指令");
        String prompt = (String) out;

        System.out.println("========= System Prompt =========");
        System.out.println(prompt);
        System.out.println("========= /System Prompt =========");
        System.out.println();

        // 关键词命中检查
        String[] markers = {
                "【安全红线】",
                "【最高优先级 · 附件与工具】",
                "【推理阶段 · 思考区输出】",
                "【行动 · 工具调用】",
                "epms_cli_*",
                "【可用 Skill】",
                "skill_hotel_order_query",
                "酒店订单查询",
                "【技能：酒店订单查询 · 操作说明】",
                "禁止编造金额",
                "【文件产物 · HTML/报告/导出】",
                "【最终回复 · 面向用户】",
                "【少表格】",
                "【附加指令】",
                "测试附加指令"
        };
        int hit = 0;
        for (String m : markers) {
            boolean present = prompt.contains(m);
            System.out.printf("  %s  %s%n", present ? "OK  " : "MISS", m);
            if (present) hit++;
        }
        System.out.println();
        System.out.println("Total: " + hit + "/" + markers.length);

        // 空目录场景
        Object out2 = compose.invoke(composer, null, Collections.emptyList(), Collections.emptyList(), null);
        String prompt2 = (String) out2;
        System.out.println();
        System.out.println("========= Empty Catalog Prompt =========");
        System.out.println(prompt2);
        System.out.println("========= /Empty Catalog Prompt =========");
        System.out.println("Should NOT contain: " + (!prompt2.contains("【可用 Skill】")));
        System.out.println("Should NOT contain: " + (!prompt2.contains("【技能：")));

        if (hit != markers.length) {
            throw new AssertionError("Marker hit " + hit + "/" + markers.length);
        }
        System.out.println("SMOKE TEST PASSED");
    }
}
