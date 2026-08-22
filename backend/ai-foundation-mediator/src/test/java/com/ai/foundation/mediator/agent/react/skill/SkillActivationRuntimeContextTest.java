package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;
import com.ai.foundation.mediator.config.AgentProperties;
import com.ai.foundation.mediator.config.AgentProperties.React;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 不引入 JUnit 的 smoke 测试：直接通过反射调用 invoker.assemblePayload 验证
 * Phase 10 + 11 验收标准：
 *  1. skill.runtimeContextTemplate 为空时，仍无条件追加【当前时间】段（系统 3 变量）
 *  2. 模板非空时，body 末尾追加【当前会话上下文】 + 渲染后的 KV 行
 *  3. 渲染时按 #{hotelCode} → 用户 KV 值替换
 *  4. 渲染时按 #{todayDate} → 系统级 getter 现场算
 *  5. 模板里【当前会话上下文】段后仍继续追加【当前时间】段（顺序：业务 → 系统）
 *  6. 系统变量集只保留 todayDate / currentTime / currentTimezone（无 conversationCode/runCode）
 */
public class SkillActivationRuntimeContextTest {

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;

        AgentProperties props = new AgentProperties();
        React reactCfg = new React();
        reactCfg.setActivationBodyPreviewMax(4000);
        props.setReact(reactCfg);
        RuntimeContextTemplateInjector injector = new RuntimeContextTemplateInjector();
        ReactSkillToolInvoker invoker = new ReactSkillToolInvoker(props, injector);

        Method assemble = ReactSkillToolInvoker.class.getDeclaredMethod(
                "assemblePayload", AgentSkillDefinition.class, AgentExecutionContext.class);
        assemble.setAccessible(true);

        // ============== Case 1: 模板为 null → 不追加【当前会话上下文】段，但仍追加【当前时间】段 ==============
        {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("hotel_booking");
            skill.setSkillName("酒店预订");
            skill.setSystemPrompt("酒店预订 skill 操作要点");
            skill.setRuntimeContextTemplate(null);

            AgentExecutionContext ctx = new AgentExecutionContext();
            Object payloadObj = assemble.invoke(invoker, skill, ctx);
            SkillActivationPayload payload = (SkillActivationPayload) payloadObj;
            String body = payload.getBody();

            assertContains("Case 1: 模板为 null 时不追加【当前会话上下文】段", body, "酒店预订 skill 操作要点");
            assertNotContains("Case 1: 模板为 null 时不追加段头", body, "【当前会话上下文】");
            assertContains("Case 1: 模板为 null 时仍无条件追加【当前时间】段", body, "【当前时间】");
            assertContains("Case 1: 含今日日期", body, "今日日期:");
            assertContains("Case 1: 含当前时间", body, "当前时间:");
            assertContains("Case 1: 含时区", body, "时区:");
            passed++;
        }

        // ============== Case 2: 模板有内容 → 追加段头 + 渲染 ==============
        {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("hotel_booking");
            skill.setSkillName("酒店预订");
            skill.setSystemPrompt("酒店预订 skill 操作要点");
            skill.setRuntimeContextTemplate(
                    "- 酒店编码（hotelCode）: #{hotelCode}\n- 集团编码（blocCode）: #{blocCode}\n- 今日: #{todayDate}");

            AgentExecutionContext ctx = new AgentExecutionContext();
            Map<String, Object> kv = new HashMap<>();
            kv.put("hotelCode", "HTL001");
            kv.put("blocCode", "BC002");
            ctx.setContextVariables(kv);

            Object payloadObj = assemble.invoke(invoker, skill, ctx);
            SkillActivationPayload payload = (SkillActivationPayload) payloadObj;
            String body = payload.getBody();

            assertContains("Case 2: 含【当前会话上下文】段头", body, "【当前会话上下文】");
            assertContains("Case 2: hotelCode 替换为 HTL001", body, "HTL001");
            assertContains("Case 2: blocCode 替换为 BC002", body, "BC002");
            assertContains("Case 2: todayDate 由系统级 getter 现场算",
                    body, java.time.LocalDate.now().toString());
            // 段头应在 systemPrompt 之后、注册工具提示之后
            int idxHeader = body.indexOf("【当前会话上下文】");
            int idxSystemPrompt = body.indexOf("酒店预订 skill 操作要点");
            int idxToolHint = body.indexOf("请从本轮已注册工具中");
            assertTrue("Case 2: 段头位置正确（在 systemPrompt 之后、tool hint 之后）",
                    idxHeader > idxSystemPrompt && idxHeader > idxToolHint,
                    "body=" + body, "idxHeader > idxSystemPrompt && idxHeader > idxToolHint");
            passed++;
        }

        // ============== Case 3: 模板里 #{hotelCode} 但 KV 池缺值 → 留空不报错 ==============
        {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("hotel_booking");
            skill.setSkillName("酒店预订");
            skill.setSystemPrompt("操作要点");
            skill.setRuntimeContextTemplate("- 酒店编码: #{hotelCode}");

            AgentExecutionContext ctx = new AgentExecutionContext();
            // ctx.contextVariables 为空

            Object payloadObj = assemble.invoke(invoker, skill, ctx);
            SkillActivationPayload payload = (SkillActivationPayload) payloadObj;
            String body = payload.getBody();

            assertContains("Case 3: 缺值时仍渲染【当前会话上下文】段", body, "【当前会话上下文】");
            assertContains("Case 3: 缺值时留空", body, "酒店编码: ");
            passed++;
        }

        // ============== Case 4: skill 系统 prompt 也为空 → body 仍正常 ==============
        {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("cs");
            skill.setSkillName("客服");
            skill.setSystemPrompt(null);
            skill.setRuntimeContextTemplate("- 时间: #{currentTime}");

            AgentExecutionContext ctx = new AgentExecutionContext();
            Object payloadObj = assemble.invoke(invoker, skill, ctx);
            SkillActivationPayload payload = (SkillActivationPayload) payloadObj;
            String body = payload.getBody();

            assertContains("Case 4: systemPrompt 为空时 body 仍含 tool hint", body, "请从本轮已注册工具中");
            assertContains("Case 4: systemPrompt 为空时仍含【当前会话上下文】段",
                    body, "【当前会话上下文】");
            assertTrue("Case 4: currentTime 由系统级 getter 算",
                    body.matches("(?s).*时间: \\d{2}:\\d{2}:\\d{2}.*"),
                    "body=" + body, "expected 正则匹配 时间: HH:mm:ss");
            passed++;
        }

        // ============== Case 5: 模板渲染 + 系统【当前时间】段，顺序 = 业务 → 系统 ==============
        {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("hotel_booking");
            skill.setSkillName("酒店预订");
            skill.setSystemPrompt("酒店预订操作要点");
            skill.setRuntimeContextTemplate("- 酒店编码: #{hotelCode}");

            AgentExecutionContext ctx = new AgentExecutionContext();
            Map<String, Object> kv = new HashMap<>();
            kv.put("hotelCode", "HTL999");
            ctx.setContextVariables(kv);

            Object payloadObj = assemble.invoke(invoker, skill, ctx);
            SkillActivationPayload payload = (SkillActivationPayload) payloadObj;
            String body = payload.getBody();

            assertContains("Case 5: 含业务段头【当前会话上下文】", body, "【当前会话上下文】");
            assertContains("Case 5: 业务段含 hotelCode=HTL999", body, "HTL999");
            assertContains("Case 5: 含系统段头【当前时间】", body, "【当前时间】");
            int idxBiz = body.indexOf("【当前会话上下文】");
            int idxSys = body.indexOf("【当前时间】");
            assertTrue("Case 5: 业务段在系统段之前",
                    idxBiz < idxSys, "bizIdx=" + idxBiz + " sysIdx=" + idxSys, "idxBiz < idxSys");
            passed++;
        }

        // ============== Case 6: 系统变量集只 3 个 + Renderer.AUTO_INJECT_VARS 重命名 ==============
        {
            java.util.Set<String> auto = com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer.AUTO_INJECT_VARS;
            assertTrue("Case 6: AUTO_INJECT_VARS 只含 3 个 key",
                    auto.size() == 3
                            && auto.contains("todayDate")
                            && auto.contains("currentTime")
                            && auto.contains("currentTimezone"),
                    "auto=" + auto, "expected {todayDate, currentTime, currentTimezone}");
            // 旧 SYSTEM_VARS 应不存在
            try {
                java.lang.reflect.Field f = com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer.class
                        .getDeclaredField("SYSTEM_VARS");
                Object old = f.get(null);
                System.err.println("[FAIL] Case 6: 旧 SYSTEM_VARS 已删除但仍存在: " + old);
                throw new AssertionError("Case 6: 旧 SYSTEM_VARS 应已删除");
            } catch (NoSuchFieldException nsf) {
                System.out.println("[PASS] Case 6: 旧 SYSTEM_VARS 字段已删除");
            }
            // 用户级 keys 不应再过滤 conversationCode / runCode
            java.util.Set<String> required =
                    com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer.extractUserRequiredKeys(
                            "- 酒店: #{hotelCode}\n- 会话: #{conversationCode}\n- Run: #{runCode}\n- 今日: #{todayDate}");
            assertTrue("Case 6: extractUserRequiredKeys 现在认为 conversationCode / runCode 也是用户级",
                    required.contains("hotelCode")
                            && required.contains("conversationCode")
                            && required.contains("runCode")
                            && !required.contains("todayDate"),
                    "required=" + required, "expected {hotelCode, conversationCode, runCode}");
            passed++;
        }

        System.out.println("=========================================");
        System.out.println("SkillActivationRuntimeContextTest: " + passed + " passed, " + failed + " failed");
        System.out.println("=========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertContains(String label, String body, String sub) {
        if (body.contains(sub)) {
            System.out.println("[PASS] " + label);
        } else {
            System.err.println("[FAIL] " + label);
            System.err.println("       expected contains: " + sub);
            System.err.println("       body: " + body);
            throw new AssertionError(label);
        }
    }

    private static void assertNotContains(String label, String body, String sub) {
        if (!body.contains(sub)) {
            System.out.println("[PASS] " + label);
        } else {
            System.err.println("[FAIL] " + label);
            System.err.println("       expected NOT contains: " + sub);
            System.err.println("       body: " + body);
            throw new AssertionError(label);
        }
    }

    private static void assertTrue(String label, boolean cond, String actual, String expected) {
        if (cond) {
            System.out.println("[PASS] " + label);
        } else {
            System.err.println("[FAIL] " + label);
            if (actual != null) {
                System.err.println("       actual:   " + actual);
            }
            if (expected != null) {
                System.err.println("       expected: " + expected);
            }
            throw new AssertionError(label);
        }
    }
}
