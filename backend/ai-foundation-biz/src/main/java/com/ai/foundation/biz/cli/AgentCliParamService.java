package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentCliParam;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentCliParamService extends IService<AgentCliParam> {

    List<AgentCliParam> listByCliId(Long cliId);

    void removeByCliId(Long cliId);
}
