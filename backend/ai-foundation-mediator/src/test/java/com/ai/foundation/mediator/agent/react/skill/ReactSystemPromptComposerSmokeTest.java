package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不引入 JUnit 的 smoke 测试：直接通过反射调用 composer 并打印各段关键词命中情况。
 * 验证 A+C 档改造验收标准：
 *  1. baseRole 段以「你是『{projectName}』助手」开头，projectName 为空时回退"AI 助手"
 *  2. 4 条【安全红线】+ 思考区 + 行动 + 工具调用（含「先调 skill_* 拿要点」指引） + 索引 + 最终回复
 *  3. skill 正文不再内联到 outer prompt（appendSkillDetail 已删），只挂索引行
 *  4. projectSystemPrompt 已下线，composer 不再接收该参数
 *  5. capabilities / agentType 全部回退后，4 段无条件渲染
 */
public class ReactSystemPromptComposerSmokeTest {

    public static void main(String[] args) throws Exception {
        ReactSystemPromptComposer composer = new ReactSystemPromptComposer();

        AgentSkillDefinition skill = new AgentSkillDefinition();
        skill.setSkillCode("hotel_order_query");
        skill.setSkillName("酒店订单查询");
        skill.setDescription("支持订单检索、详情、状态变更");
        skill.setSystemPrompt("1. 先根据订单号或客户信息检索订单\n2. 返回订单详情时禁止编造金额");
        skill.setState(1);
        List<AgentSkillDefinition> skills = new ArrayList<>();
        skills.add(skill);

        AgentCliCommand cli = new AgentCliCommand();
        cli.setId(1L);
        cli.setCommandName("epms_order_list");
        cli.setCommandPrefix("epms");
        cli.setCommandType("API");
        cli.setState(1);
        List<AgentCliCommand> cliList = new ArrayList<>();
        cliList.add(cli);

        // 4-arg 签名：compose(skills, cliList, userSystemPrompt, projectName)
        Method compose = ReactSystemPromptComposer.class.getMethod(
                "compose", List.class, List.class, String.class, String.class);

        Object out = compose.invoke(composer, skills, cliList, "测试附加指令", "星辰旅游助手");
        String prompt = (String) out;

        System.out.println("========= System Prompt (projectName=星辰旅游助手, 无 projectSystemPrompt) =========");
        System.out.println(prompt);
        System.out.println("========= /System Prompt =========");
        System.out.println();

        String[] positiveMarkers = {
                "你是『星辰旅游助手』助手",                // projectName 注入 baseRole
                "采用 ReAct（推理+行动）模式",            // baseRole ReAct
                "【安全红线】",                            // 4 条合并段
                "不得向用户披露、罗列、汇总",             // 红线 1
                "不得编造未出现在工具清单中的工具",        // 红线 2
                "必填参数（schema.required）",             // 红线 3
                "【最高优先级 · 附件与工具】",             // 段无条件渲染
                "【推理阶段 · 思考区输出】",              // 思考区
                "【行动 · 工具调用】",                    // 行动
                "业务查询先调用 skill_* 工具",            // 业务→skill 优先
                "禁止编造未出现在清单中的工具",
                "【可用 Skill】",                          // 索引段
                "skill_hotel_order_query",                // 索引行
                "酒店订单查询",
                "【最终回复 · 面向用户】",                // 最终回复
                "【少表格】",
                "【附加指令】",
                "测试附加指令"
        };
        String[] negativeMarkers = {
                "【技能：酒店订单查询 · 操作说明】",       // appendSkillDetail 已删：正文不内联
                "先根据订单号或客户信息检索订单",          // systemPrompt 正文不应出现
                "禁止编造金额",                            // systemPrompt 正文不应出现
                "【平台硬规则 · 优先级声明】",             // P1 独立段已撤销
                "【文件产物 · HTML/报告/导出】",          // 文件产物段已撤销
                "【最终回复 · GUI 操作】"                  // GUI 段已撤销
        };
        int hit = 0;
        for (String m : positiveMarkers) {
            boolean present = prompt.contains(m);
            System.out.printf("  %s  %s%n", present ? "OK  " : "MISS", m);
            if (present) hit++;
        }
        System.out.println("--- negative markers (按需激活模式下不应出现) ---");
        int correctNegative = 0;
        for (String m : negativeMarkers) {
            boolean present = prompt.contains(m);
            System.out.printf("  %s  %s%n", !present ? "OK  " : "MISS", m);
            if (!present) correctNegative++;
        }
        System.out.println();
        System.out.println("Positive hit: " + hit + "/" + positiveMarkers.length);
        System.out.println("Negative hit: " + correctNegative + "/" + negativeMarkers.length);

        // 空目录场景
        Object out2 = compose.invoke(composer, Collections.emptyList(), Collections.emptyList(), null, null);
        String prompt2 = (String) out2;
        System.out.println();
        System.out.println("========= Empty Catalog Prompt =========");
        System.out.println(prompt2);
        System.out.println("========= /Empty Catalog Prompt =========");
        System.out.println("Should contain 你是『AI 助手』助手 (空 projectName 回退): "
                + (prompt2.contains("你是『AI 助手』助手")));
        System.out.println("Should NOT contain 【可用 Skill】: " + (!prompt2.contains("【可用 Skill】")));

        if (hit != positiveMarkers.length || correctNegative != negativeMarkers.length
                || !prompt2.contains("你是『AI 助手』助手")
                || prompt2.contains("【可用 Skill】")) {
            throw new AssertionError(String.format(
                    "Marker hit positive=%d/%d, negative=%d/%d, empty-fallback=%s, empty-skill-index=%s",
                    hit, positiveMarkers.length, correctNegative, negativeMarkers.length,
                    prompt2.contains("你是『AI 助手』助手"),
                    !prompt2.contains("【可用 Skill】")));
        }
        System.out.println("SMOKE TEST PASSED");
    }
}
