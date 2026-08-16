package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.mapper.AgentCliCommandMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentCliCommandServiceImpl extends ServiceImpl<AgentCliCommandMapper, AgentCliCommand>
        implements AgentCliCommandService {

    @Override
    public IPage<AgentCliCommand> page(Page<AgentCliCommand> page, String keyword, String commandType,
                                        String commandPrefix, Integer state) {
        LambdaQueryWrapper<AgentCliCommand> wrapper = new LambdaQueryWrapper<AgentCliCommand>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(AgentCliCommand::getCommandName, keyword)
                        .or().like(AgentCliCommand::getDescription, keyword))
                .eq(commandType != null && !commandType.isBlank(), AgentCliCommand::getCommandType, commandType)
                .eq(commandPrefix != null && !commandPrefix.isBlank(), AgentCliCommand::getCommandPrefix, commandPrefix)
                .eq(state != null, AgentCliCommand::getState, state)
                .orderByDesc(AgentCliCommand::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    public boolean existsByCommandName(String commandName, Long excludeId) {
        LambdaQueryWrapper<AgentCliCommand> wrapper = new LambdaQueryWrapper<AgentCliCommand>()
                .eq(AgentCliCommand::getCommandName, commandName)
                .ne(excludeId != null, AgentCliCommand::getId, excludeId);
        return this.count(wrapper) > 0;
    }

    @Override
    public List<AgentCliCommand> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<AgentCliCommand>()
                .in(AgentCliCommand::getId, ids));
    }
}
