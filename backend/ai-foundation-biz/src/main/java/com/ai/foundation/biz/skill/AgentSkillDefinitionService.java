package com.ai.foundation.biz.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

public interface AgentSkillDefinitionService extends IService<AgentSkillDefinition> {

    IPage<AgentSkillDefinition> page(Page<AgentSkillDefinition> page, String keyword,
                                      String skillType, Integer state);

    boolean existsBySkillCode(String skillCode, Long excludeId);

    List<AgentSkillDefinition> listByIds(List<Long> ids);

    /**
     * 收集项目下所有 skill 模板里出现的"用户级必传"占位符 key（已扣掉系统级白名单）。
     * <p>
     * 用于 {@code AgentConversationMedService.create} 校验调用方是否传齐必需 KV。
     * 系统级变量（{@code todayDate} / {@code currentTime} 等）由平台自动注入，调用方
     * 不传也不算缺失。
     *
     * @param projectId 项目 ID，可为 null（返回空集）
     * @return 必传 key 并集
     */
    Set<String> collectUserRequiredContextKeys(Long projectId);
}
