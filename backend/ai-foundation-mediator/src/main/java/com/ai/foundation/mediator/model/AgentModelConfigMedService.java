package com.ai.foundation.mediator.model;

import com.ai.foundation.biz.converter.ModelConfigConverter;
import com.ai.foundation.biz.model.AgentModelConfigService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.enums.ModelTypeEnum;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentModelConfig;
import com.ai.foundation.facade.dto.model.AgentModelConfigDTO;
import com.ai.foundation.facade.dto.model.AgentModelConfigPageRequest;
import com.ai.foundation.facade.dto.model.AgentModelConfigSaveRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentModelConfigMedService {

    private final AgentModelConfigService modelConfigService;
    private final ModelConfigConverter converter;

    public PageResult<AgentModelConfigDTO> page(AgentModelConfigPageRequest request) {
        Page<AgentModelConfig> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<AgentModelConfig> result = modelConfigService.page(page,
                request.getProjectId(), request.getModelName(), request.getModelType(), request.getState());
        List<AgentModelConfigDTO> records = result.getRecords().stream().map(converter::toDto).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public AgentModelConfigDTO detail(Long id) {
        AgentModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模型配置不存在");
        }
        return converter.toDto(config);
    }

    public void create(AgentModelConfigSaveRequest request, String operator) {
        ModelTypeEnum.of(request.getModelType());
        AgentModelConfig entity = converter.toEntity(request);
        if (entity.getState() == null) {
            entity.setState(CommonConstants.STATE_ENABLED);
        }
        entity.setCreateUser(operator);
        entity.setModifyUser(operator);
        modelConfigService.save(entity);
        log.info("创建模型配置成功 projectId={} model={} type={} operator={}",
                request.getProjectId(), request.getModelName(), request.getModelType(), operator);
    }

    public void update(AgentModelConfigSaveRequest request, String operator) {
        AgentModelConfig existing = modelConfigService.getById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模型配置不存在");
        }
        ModelTypeEnum.of(request.getModelType());
        converter.updateEntity(request, existing);
        existing.setModifyUser(operator);
        modelConfigService.updateById(existing);
        log.info("更新模型配置 id={} operator={}", request.getId(), operator);
    }

    public void delete(Long id, String operator) {
        AgentModelConfig existing = modelConfigService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模型配置不存在");
        }
        modelConfigService.removeById(id);
        log.info("删除模型配置 id={} operator={}", id, operator);
    }
}
