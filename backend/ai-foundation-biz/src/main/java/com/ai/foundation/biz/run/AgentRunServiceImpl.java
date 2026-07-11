package com.ai.foundation.biz.run;

import com.ai.foundation.dal.entity.AgentRun;
import com.ai.foundation.dal.mapper.AgentRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRun>
        implements AgentRunService {

    @Override
    public AgentRun getByRunCode(String runCode) {
        if (runCode == null || runCode.isBlank()) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunCode, runCode.trim())
                .last("limit 1"));
    }
}
