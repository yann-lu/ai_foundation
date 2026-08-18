package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 能力 Handler 注册中心。Spring 启动时按 {@link SkillCapabilityHandler#capabilityId()} 聚合。
 * <p>
 * 当前阶段所有 Handler 对所有 Skill 生效；后续可根据 skill.skillCode / skillType 过滤匹配。
 */
@Slf4j
@Component
public class SkillCapabilityRegistry {

    private final Map<String, SkillCapabilityHandler> handlers = new LinkedHashMap<>();

    public SkillCapabilityRegistry(List<SkillCapabilityHandler> beans) {
        if (beans == null) {
            return;
        }
        for (SkillCapabilityHandler handler : beans) {
            if (handler == null) {
                continue;
            }
            String id = handler.capabilityId();
            if (id == null || id.isBlank()) {
                log.warn("SkillCapabilityHandler capabilityId 为空, 跳过注册: {}", handler.getClass().getName());
                continue;
            }
            SkillCapabilityHandler prev = handlers.put(id, handler);
            if (prev != null) {
                log.warn("SkillCapabilityHandler capabilityId={} 重复注册, 后者覆盖前者: {} -> {}",
                        id, prev.getClass().getName(), handler.getClass().getName());
            }
        }
        log.info("SkillCapabilityRegistry 初始化完成, handlerCount={}, ids={}",
                handlers.size(), handlers.keySet());
    }

    /**
     * 列出对指定 Skill 生效的 Handler 列表。
     * <p>
     * 当前实现：所有 Handler 都生效。后续可按 Skill 元数据过滤（如只对含 chart 类标签的 Skill 生效）。
     */
    public List<SkillCapabilityHandler> listMatching(AgentSkillDefinition skill) {
        return new ArrayList<>(handlers.values());
    }

    public Collection<SkillCapabilityHandler> all() {
        return handlers.values();
    }
}
