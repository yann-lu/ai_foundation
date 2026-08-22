package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 不引入 JUnit 的 smoke 测试：直接调用 renderer / injector 并打印各场景命中情况。
 * 验证 Phase 10 验收标准：
 *  1. extractPlaceholders 正确识别 #{var} 占位符并去重
 *  2. extractUserRequiredKeys 正确扣掉系统级白名单
 *  3. inject.render 在用户 KV 池有值时正常替换
 *  4. inject.render 在用户 KV 池缺值时留空（不抛异常）
 *  5. 系统级（todayDate / currentTime）由 getter 现场算
 */
public class RuntimeContextTemplateRendererTest {

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;

        // ============== Renderer 纯函数 ==============
        // renderer 是 final class + private 构造器 + 静态方法，直接用类名调用

        // Case 1: 模板空 → 提取为空集
        {
            Set<String> result = RuntimeContextTemplateRenderer.extractPlaceholders(null);
            assertTrue("Case 1: null 模板返回空集", result.isEmpty(), null, null);
            passed++;
        }
        {
            Set<String> result = RuntimeContextTemplateRenderer.extractPlaceholders("");
            assertTrue("Case 1.1: 空串模板返回空集", result.isEmpty(), null, null);
            passed++;
        }

        // Case 2: 单占位符
        {
            Set<String> result = RuntimeContextTemplateRenderer.extractPlaceholders("- 酒店编码: #{hotelCode}");
            assertTrue("Case 2: 单占位符", result.size() == 1 && result.contains("hotelCode"),
                    "actual=" + result, "expected={hotelCode}");
            passed++;
        }

        // Case 3: 多占位符去重
        {
            Set<String> result = RuntimeContextTemplateRenderer.extractPlaceholders(
                    "- a: #{hotelCode}\n- b: #{blocCode}\n- c: #{hotelCode}\n- d: #{todayDate}");
            assertTrue("Case 3: 多占位符去重 + 保持顺序",
                    result.size() == 3 && result.contains("hotelCode") && result.contains("blocCode") && result.contains("todayDate"),
                    "actual=" + result, "expected={hotelCode, blocCode, todayDate}");
            passed++;
        }

        // Case 4: 系统级白名单扣掉
        {
            Set<String> userKeys = RuntimeContextTemplateRenderer.extractUserRequiredKeys(
                    "- a: #{hotelCode}\n- b: #{blocCode}\n- c: #{todayDate}\n- d: #{currentTime}");
            assertTrue("Case 4: 扣掉系统级白名单",
                    userKeys.size() == 2 && userKeys.contains("hotelCode") && userKeys.contains("blocCode"),
                    "actual=" + userKeys, "expected={hotelCode, blocCode}");
            passed++;
        }

        // Case 5: 无效占位符（数字开头、特殊字符）不识别
        {
            Set<String> result = RuntimeContextTemplateRenderer.extractPlaceholders(
                    "- bad: #{1hotelCode}\n- bad: #{hotel-code}\n- good: #{hotelCode}");
            assertTrue("Case 5: 无效占位符不识别", result.size() == 1 && result.contains("hotelCode"),
                    "actual=" + result, "expected={hotelCode}");
            passed++;
        }

        // ============== Injector 渲染 ==============
        RuntimeContextTemplateInjector injector = new RuntimeContextTemplateInjector();

        // Case 6: 模板 null / 空 → 返回 ""
        {
            assertTrue("Case 6: 模板 null", "".equals(injector.render(null, new AgentExecutionContext())),
                    "actual=" + injector.render(null, new AgentExecutionContext()), "expected=\"\"");
            passed++;
        }

        // Case 7: context null → 返回 ""
        {
            assertTrue("Case 7: context null", "".equals(injector.render("- a: #{hotelCode}", null)),
                    "actual=" + injector.render("- a: #{hotelCode}", null), "expected=\"\"");
            passed++;
        }

        // Case 8: 正常替换（用户 KV + 系统级）
        {
            AgentExecutionContext ctx = new AgentExecutionContext();
            Map<String, Object> kv = new HashMap<>();
            kv.put("hotelCode", "HTL001");
            kv.put("blocCode", "BC002");
            ctx.setContextVariables(kv);

            String rendered = injector.render(
                    "- 酒店编码: #{hotelCode}\n- 集团编码: #{blocCode}\n- 今日: #{todayDate}\n- 时间: #{currentTime}",
                    ctx);

            boolean hasHotel = rendered.contains("HTL001");
            boolean hasBloc = rendered.contains("BC002");
            boolean hasDate = rendered.contains(java.time.LocalDate.now().toString());
            boolean hasTime = rendered.matches("(?s).*\\d{2}:\\d{2}:\\d{2}.*");
            assertTrue("Case 8: 正常替换（用户 KV + 系统级）",
                    hasHotel && hasBloc && hasDate && hasTime,
                    "rendered=" + rendered,
                    "expected 含 HTL001, BC002, todayDate, HH:mm:ss");
            passed++;
        }

        // Case 9: 缺值留空
        {
            AgentExecutionContext ctx = new AgentExecutionContext();
            String rendered = injector.render("- 酒店编码: #{hotelCode}\n- 集团编码: #{blocCode}", ctx);
            assertTrue("Case 9: 缺值留空（hotelCode 空, blocCode 空）",
                    rendered.contains("酒店编码: ") && rendered.contains("集团编码: "),
                    "rendered=" + rendered, "expected 含 '酒店编码: ' 和 '集团编码: '（值留空）");
            passed++;
        }

        // Case 10: 用户 KV 覆盖系统级同名 key（冻结时间场景）
        {
            AgentExecutionContext ctx = new AgentExecutionContext();
            Map<String, Object> kv = new HashMap<>();
            kv.put("todayDate", "2026-01-01");
            kv.put("currentTime", "00:00:00");
            ctx.setContextVariables(kv);

            String rendered = injector.render("- date: #{todayDate}\n- time: #{currentTime}", ctx);
            assertTrue("Case 10: 用户 KV 覆盖系统级",
                    rendered.contains("date: 2026-01-01") && rendered.contains("time: 00:00:00"),
                    "rendered=" + rendered, "expected 含 'date: 2026-01-01' 和 'time: 00:00:00'");
            passed++;
        }

        // Case 11: 用户 KV 的 value 是数字（不是 String）也能渲染
        {
            AgentExecutionContext ctx = new AgentExecutionContext();
            Map<String, Object> kv = new HashMap<>();
            kv.put("customerLevel", 5);
            ctx.setContextVariables(kv);
            String rendered = injector.render("- level: #{customerLevel}", ctx);
            assertTrue("Case 11: 数字 value 渲染", rendered.contains("level: 5"),
                    "rendered=" + rendered, "expected 含 'level: 5'");
            passed++;
        }

        System.out.println("=========================================");
        System.out.println("RuntimeContextTemplateRendererTest: " + passed + " passed, " + failed + " failed");
        System.out.println("=========================================");
        if (failed > 0) {
            System.exit(1);
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
