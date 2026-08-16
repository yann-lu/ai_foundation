package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliRecallTag;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentCliRecallTagService extends IService<AgentCliRecallTag> {

    List<AgentCliRecallTag> listByCliId(Long cliId);

    void removeByCliId(Long cliId);
}
