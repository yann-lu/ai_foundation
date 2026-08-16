package com.ai.foundation.biz.schema;

import com.ai.foundation.dal.entity.AgentApiSchemaConfig;
import com.ai.foundation.dal.mapper.AgentApiSchemaConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentApiSchemaConfigServiceImpl
        extends ServiceImpl<AgentApiSchemaConfigMapper, AgentApiSchemaConfig>
        implements AgentApiSchemaConfigService {

    @Override
    public IPage<AgentApiSchemaConfig> page(Page<AgentApiSchemaConfig> page, String keyword, Integer state) {
        LambdaQueryWrapper<AgentApiSchemaConfig> wrapper = new LambdaQueryWrapper<AgentApiSchemaConfig>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(AgentApiSchemaConfig::getSchemaCode, keyword)
                        .or().like(AgentApiSchemaConfig::getSchemaName, keyword))
                .eq(state != null, AgentApiSchemaConfig::getState, state)
                .orderByDesc(AgentApiSchemaConfig::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    public boolean existsByCode(String schemaCode, Long excludeId) {
        LambdaQueryWrapper<AgentApiSchemaConfig> wrapper = new LambdaQueryWrapper<AgentApiSchemaConfig>()
                .eq(AgentApiSchemaConfig::getSchemaCode, schemaCode)
                .ne(excludeId != null, AgentApiSchemaConfig::getId, excludeId);
        return this.count(wrapper) > 0;
    }

    @Override
    public List<AgentApiSchemaConfig> listEnabled() {
        return this.list(new LambdaQueryWrapper<AgentApiSchemaConfig>()
                .eq(AgentApiSchemaConfig::getState, 1)
                .orderByAsc(AgentApiSchemaConfig::getSchemaName));
    }

    @Override
    public AgentApiSchemaConfig getBySchemaCode(String schemaCode) {
        if (schemaCode == null || schemaCode.isBlank()) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<AgentApiSchemaConfig>()
                .eq(AgentApiSchemaConfig::getSchemaCode, schemaCode)
                .last("limit 1"));
    }

}
