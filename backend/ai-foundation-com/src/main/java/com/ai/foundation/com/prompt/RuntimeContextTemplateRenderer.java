package com.ai.foundation.com.prompt;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行时上下文模板渲染器：把 {@code skill.runtime_context_template} 里的
 * {@code #{varName}} 占位符替换为调用方传入的 KV 值。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>纯函数无 Spring 依赖，部署到 com 模块供 biz / mediator 共同引用，避免循环依赖</li>
 *   <li>占位符仅匹配 {@code [A-Za-z][A-Za-z0-9_]*}，不识别嵌套、不识别表达式（防注入）</li>
 *   <li>缺值时留空字符串，不抛异常（保证 skill 激活链路不因上下文缺失而中断）</li>
 *   <li>系统级变量（{@link #AUTO_INJECT_VARS}）由调用方在 create 校验时跳过，由
 *       {@code ReactSkillToolInvoker} 无条件追加到 body 末尾</li>
 * </ul>
 */
public final class RuntimeContextTemplateRenderer {

    /** 占位符正则：#{identifier}，identifier 形如 hotelCode / todayDate。 */
    public static final Pattern PLACEHOLDER = Pattern.compile("#\\{([A-Za-z][A-Za-z0-9_]*?)}");

    /**
     * 平台自动注入的系统级变量名（仅 3 个：日期 / 时间 / 时区）。
     * <ul>
     *   <li>create 会话校验时，模板里出现的这些 key 不视为"用户级必传"，跳过</li>
     *   <li>skill 激活时由 {@code ReactSkillToolInvoker} 无条件追加，无须模板写</li>
     *   <li>原 conversationCode / runCode 已删除——内部 ID 不再进入 skill 提示词</li>
     * </ul>
     */
    public static final Set<String> AUTO_INJECT_VARS = Set.of(
            "todayDate",
            "currentTime",
            "currentTimezone"
    );

    private RuntimeContextTemplateRenderer() {
    }

    /**
     * 提取模板中出现的所有占位符 key（去重、保持出现顺序）。
     *
     * @param template 模板字符串，可为 null
     * @return key 集合（去重），模板为 null 时返回空集
     */
    public static Set<String> extractPlaceholders(String template) {
        Set<String> result = new LinkedHashSet<>();
        if (template == null || template.isEmpty()) {
            return result;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * 提取模板中出现的"用户级"占位符 key（扣掉系统级白名单），用于 create 会话时的
     * 必传校验。
     *
     * @param template 模板字符串，可为 null
     * @return 用户级 key 集合
     */
    public static Set<String> extractUserRequiredKeys(String template) {
        Set<String> all = extractPlaceholders(template);
        Set<String> userKeys = new LinkedHashSet<>(all);
        userKeys.removeAll(AUTO_INJECT_VARS);
        return userKeys;
    }
}
