package com.ai.foundation.mediator.schema;

import com.ai.foundation.biz.converter.ApiSchemaConfigConverter;
import com.ai.foundation.biz.schema.AgentApiSchemaConfigService;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentApiSchemaConfig;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigDTO;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigPageRequest;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigSaveRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApiSchemaMedService {

    private final AgentApiSchemaConfigService schemaService;
    private final ApiSchemaConfigConverter converter;

    public PageResult<ApiSchemaConfigDTO> page(ApiSchemaConfigPageRequest request) {
        Page<AgentApiSchemaConfig> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<AgentApiSchemaConfig> result = schemaService.page(page,
                request.getKeyword(), request.getState());
        List<ApiSchemaConfigDTO> records = result.getRecords().stream()
                .map(converter::toDto)
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ApiSchemaConfigDTO detail(Long id) {
        AgentApiSchemaConfig entity = schemaService.getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Schema配置不存在");
        }
        return converter.toDto(entity);
    }

    public List<ApiSchemaConfigDTO> listEnabled() {
        return schemaService.listEnabled().stream()
                .map(converter::toDto)
                .toList();
    }

    public Long create(ApiSchemaConfigSaveRequest request, String operator) {
        if (schemaService.existsByCode(request.getSchemaCode(), null)) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "Schema编码已存在");
        }
        AgentApiSchemaConfig entity = converter.toEntity(request);
        if (entity.getState() == null) {
            entity.setState(CommonConstants.STATE_ENABLED);
        }
        entity.setCreateUser(operator);
        entity.setModifyUser(operator);
        schemaService.save(entity);
        log.info("创建Schema配置成功 schemaCode={} operator={}", request.getSchemaCode(), operator);
        return entity.getId();
    }

    public void update(ApiSchemaConfigSaveRequest request, String operator) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "ID不能为空");
        }
        AgentApiSchemaConfig existing = schemaService.getById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Schema配置不存在");
        }
        if (schemaService.existsByCode(request.getSchemaCode(), request.getId())) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "Schema编码已存在");
        }
        converter.updateEntity(request, existing);
        existing.setModifyUser(operator);
        schemaService.updateById(existing);
        log.info("更新Schema配置成功 id={} operator={}", request.getId(), operator);
    }

    public void delete(Long id, String operator) {
        AgentApiSchemaConfig existing = schemaService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Schema配置不存在");
        }
        schemaService.removeById(id);
        log.info("删除Schema配置 id={} operator={}", id, operator);
    }
}
