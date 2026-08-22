package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 端到端验证：用 hotel_booking 真实 systemPrompt 走 compose，验证 A+C 档改造后 outer prompt 行为。
 * <p>
 * 关键不变量：
 *  1. baseRole 段以「你是『星辰旅游助手』助手」开头（projectName 注入）
 *  2. 没有 projectSystemPrompt 段（已下线）
 *  3. skill 正文不再内联（看不到"你具备全球酒店实时查询与预订能力"等正文）
 *  4. 只挂一行索引 `1. skill_hotel_booking — 酒店预订：...`
 *  5. P1 的优先级声明 / 文件产物 / GUI 段不再出现
 */
public class EndToEndHotelBookingTest {

    public static void main(String[] args) throws Exception {
        ReactSystemPromptComposer composer = new ReactSystemPromptComposer();

        AgentSkillDefinition hotelBooking = new AgentSkillDefinition();
        hotelBooking.setSkillCode("hotel_booking");
        hotelBooking.setSkillName("酒店预订");
        hotelBooking.setDescription("RollingGo 全球酒店实时查询与预订：按城市/景点/机场/车站搜索酒店，支持星级、预算、标签（泳池/含早/亲子/宠物友好）筛选，查房型与实时报价，引导完成预订下单与订单查询。");
        hotelBooking.setSystemPrompt("你具备全球酒店实时查询与预订能力（由 RollingGo 酒店服务提供）……【完整 5783 字节原文，节选】");
        hotelBooking.setState(1);

        // 4-arg 签名：compose(skills, cliList, userSystemPrompt, projectName)
        Method compose = ReactSystemPromptComposer.class.getMethod(
                "compose", List.class, List.class, String.class, String.class);

        Object out = compose.invoke(composer,
                List.of(hotelBooking),
                Collections.emptyList(),
                "测试附加指令",
                "星辰旅游助手");
        String prompt = (String) out;

        System.out.println("========= 端到端：hotel_booking 渲染结果（A 档：无项目级 system_prompt） =========");
        System.out.println(prompt);
        System.out.println("========= /渲染结果 =========");
        System.out.println();

        String[] expected = {
                "你是『星辰旅游助手』助手",            // projectName 注入
                "【安全红线】",                        // 4 条合并段
                "ReAct（推理+行动）",
                "【最高优先级 · 附件与工具】",          // 段无条件渲染
                "【推理阶段 · 思考区输出】",
                "【行动 · 工具调用】",
                "业务查询先调用 skill_* 工具",         // C 档新规
                "【可用 Skill】（仅可调用下列 Skill 工具，工具名格式 `skill_<skillCode>`，skillCode 来自下方列表）",
                "1. skill_hotel_booking",             // 索引行
                "酒店预订",
                "RollingGo 全球酒店实时查询与预订",    // description
                "【最终回复 · 面向用户】",
                "【少表格】",
                "【附加指令】",
                "测试附加指令"
        };
        String[] notExpected = {
                "你是【星辰旅游助手】的智能管家",       // A 档：无项目自配段
                "前厅、客房、财务",                    // A 档：无项目自配业务范围
                "【平台硬规则 · 优先级声明】",          // P1 独立段已撤销
                "【技能：酒店预订 · 操作说明】",        // C2 删 appendSkillDetail
                "你具备全球酒店实时查询与预订能力",     // systemPrompt 正文不应出现
                "Step 0：登录授权检查",                // skill 正文不应出现
                "【文件产物 · HTML/报告/导出】",        // C 档撤销
                "【最终回复 · GUI 操作】",              // C 档撤销
                "操作说明（兼容）",                    // P4 已回退
                "【工作流程】",                        // P4 已回退
                "【安全门控】",                        // P4 已回退
                "【输出规范】"                          // P4 已回退
        };

        int hit = 0;
        for (String m : expected) {
            boolean present = prompt.contains(m);
            System.out.printf("  %s  %s%n", present ? "OK  " : "MISS", m);
            if (present) hit++;
        }
        int absentHit = 0;
        for (String m : notExpected) {
            boolean absent = !prompt.contains(m);
            System.out.printf("  %s  (not expected) %s%n", absent ? "OK  " : "MISS", m);
            if (absent) absentHit++;
        }

        if (hit != expected.length || absentHit != notExpected.length) {
            throw new AssertionError(String.format(
                    "Marker hit expected=%d/%d, notExpected=%d/%d",
                    hit, expected.length, absentHit, notExpected.length));
        }
        System.out.println();
        System.out.println("END-TO-END TEST PASSED — A 档项目级 system_prompt 已下线，baseRole 由 projectName 驱动");
    }
}
