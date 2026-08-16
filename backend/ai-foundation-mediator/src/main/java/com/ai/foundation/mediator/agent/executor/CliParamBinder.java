package com.ai.foundation.mediator.agent.executor;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentCliParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CliParamBinder {

    public Map<String, Object> bind(CliParamBindContext context, List<AgentCliParam> paramDefs,
                                     String modelName) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (paramDefs == null || paramDefs.isEmpty()) {
            return result;
        }
        Map<String, Object> prefilled = context != null && context.getPrefilledParams() != null
                ? context.getPrefilledParams()
                : Map.of();

        for (AgentCliParam param : paramDefs) {
            String paramName = param.getParamName();
            if (StringUtils.isBlank(paramName)) {
                continue;
            }
            Object value = resolveParamValue(param, prefilled);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                result.put(paramName, value);
            } else if (Boolean.TRUE.equals(param.getIsRequired() == 1)) {
                log.warn("CliParamBinder missing required param: {} (command={})",
                        paramName, context != null ? context.getCliCommandName() : "unknown");
            }
        }
        return result;
    }

    private Object resolveParamValue(AgentCliParam param, Map<String, Object> prefilled) {
        String paramName = param.getParamName();
        if (prefilled.containsKey(paramName)) {
            Object val = prefilled.get(paramName);
            if (val != null && StringUtils.isNotBlank(String.valueOf(val))) {
                return val;
            }
        }
        if (StringUtils.isNotBlank(param.getDefaultValue())) {
            return param.getDefaultValue();
        }
        return null;
    }
}
