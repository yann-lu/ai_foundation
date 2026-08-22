package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 模板注入器：持有 {@link AgentExecutionContext} 引用，把模板里的 {@code #{var}} 占位符
 * 替换为 KV 池值（用户 KV 优先，缺值时回退系统级 getter）。
 * <p>
 * 与 {@link RuntimeContextTemplateRenderer} 的关系：renderer 是 com 模块的纯函数，
 * 这里负责把"context → KV 池"的运行时绑定逻辑封装起来，避免 com 模块依赖 mediator。
 */
@Component
public class RuntimeContextTemplateInjector {

    /**
     * 渲染模板。
     *
     * @param template 模板字符串，可为 null
     * @param context  执行上下文，可为 null
     * @return 渲染后字符串；模板为空或 context 为 null 时返回 ""
     */
    public String render(String template, AgentExecutionContext context) {
        if (template == null || template.isEmpty() || context == null) {
            return "";
        }
        Map<String, String> pool = buildPool(context);
        String result = template;
        for (Map.Entry<String, String> entry : pool.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            // 简单 replaceAll 不行（需转义特殊字符），用占位符重新匹配替换
            result = result.replace("#{" + key + "}", value);
        }
        return result;
    }

    private Map<String, String> buildPool(AgentExecutionContext context) {
        Map<String, String> pool = new HashMap<>();
        // 1) 系统级（3 个：todayDate / currentTime / currentTimezone）—— 业务方模板
        //    里若写了 #{todayDate} 等占位符，会被这里的系统值替换。
        //    无条件追加到 skill body 末尾的逻辑在 ReactSkillToolInvoker 里独立完成，
        //    不依赖模板是否配置。
        pool.put("todayDate", nullSafe(context.getTodayDate()));
        pool.put("currentTime", nullSafe(context.getCurrentTime()));
        pool.put("currentTimezone", nullSafe(context.getCurrentTimezone()));
        // 2) 用户级 KV（覆盖系统级同名 key，调用方可用此机制"冻结时间"做测试）
        Map<String, Object> userKv = context.getContextVariables();
        if (userKv != null) {
            for (Map.Entry<String, Object> e : userKv.entrySet()) {
                Object v = e.getValue();
                if (v == null) {
                    continue;
                }
                pool.put(e.getKey(), String.valueOf(v));
            }
        }
        return pool;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
