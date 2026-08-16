package com.ai.foundation.biz.cli;

import com.ai.foundation.dal.entity.AgentPageParam;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentPageParamService extends IService<AgentPageParam> {

    List<AgentPageParam> listByPageId(Long pageId);

    void removeByPageId(Long pageId);
}
