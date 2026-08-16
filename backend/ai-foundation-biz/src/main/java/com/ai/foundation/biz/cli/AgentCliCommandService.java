package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentCliCommandService extends IService<AgentCliCommand> {

    IPage<AgentCliCommand> page(Page<AgentCliCommand> page, String keyword, String commandType,
                                 String commandPrefix, Integer state);

    boolean existsByCommandName(String commandName, Long excludeId);

    List<AgentCliCommand> listByIds(List<Long> ids);
}
