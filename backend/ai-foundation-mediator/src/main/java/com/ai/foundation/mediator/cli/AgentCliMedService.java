package com.ai.foundation.mediator.cli;

import com.ai.foundation.biz.cli.AgentCliCommandService;
import com.ai.foundation.biz.cli.AgentCliParamService;
import com.ai.foundation.biz.cli.AgentCliRecallTagService;
import com.ai.foundation.biz.cli.AgentPageDefinitionService;
import com.ai.foundation.biz.cli.AgentProjectCliMappingService;
import com.ai.foundation.biz.cli.AgentToolDefinitionService;
import com.ai.foundation.biz.converter.CliConverter;
import com.ai.foundation.com.constant.CommonConstants;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentCliParam;
import com.ai.foundation.dal.entity.AgentCliRecallTag;
import com.ai.foundation.dal.entity.AgentPageDefinition;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.ai.foundation.facade.dto.cli.CliCommandDTO;
import com.ai.foundation.facade.dto.cli.CliCommandDetailDTO;
import com.ai.foundation.facade.dto.cli.CliCommandPageRequest;
import com.ai.foundation.facade.dto.cli.CliCommandSaveRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCliMedService {

    private final AgentCliCommandService cliCommandService;
    private final AgentCliParamService cliParamService;
    private final AgentToolDefinitionService toolDefinitionService;
    private final AgentPageDefinitionService pageDefinitionService;
    private final AgentCliRecallTagService recallTagService;
    private final AgentProjectCliMappingService projectCliMappingService;
    private final CliConverter converter;

    public PageResult<CliCommandDTO> page(CliCommandPageRequest request) {
        Page<AgentCliCommand> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<AgentCliCommand> result = cliCommandService.page(page,
                request.getKeyword(), request.getCommandType(),
                request.getCommandPrefix(), request.getState());
        List<CliCommandDTO> records = result.getRecords().stream()
                .map(entity -> {
                    CliCommandDTO dto = converter.toDto(entity);
                    dto.setBoundCount(projectCliMappingService.countByCliId(entity.getId()));
                    return dto;
                })
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CliCommandDetailDTO detail(Long id) {
        AgentCliCommand command = cliCommandService.getById(id);
        if (command == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "CLI 命令不存在");
        }
        CliCommandDetailDTO detail = converter.toDetailDto(command);
        detail.setParams(converter.toParamDtoList(cliParamService.listByCliId(id)));
        if ("API".equals(command.getCommandType())) {
            detail.setTool(converter.toToolDto(toolDefinitionService.getByCliId(id)));
        } else if ("PAGE".equals(command.getCommandType())) {
            detail.setPage(converter.toPageDto(pageDefinitionService.getByCliId(id)));
        }
        detail.setRecallTags(converter.toRecallTagDtoList(recallTagService.listByCliId(id)));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CliCommandSaveRequest request, String operator) {
        validate(request);
        if (cliCommandService.existsByCommandName(request.getCommandName(), null)) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "命令名已存在");
        }
        AgentCliCommand entity = converter.toEntity(request);
        if (entity.getState() == null) {
            entity.setState(CommonConstants.STATE_ENABLED);
        }
        entity.setCreateUser(operator);
        entity.setModifyUser(operator);
        cliCommandService.save(entity);
        saveRelated(entity.getId(), request);
        log.info("创建 CLI 命令成功 commandName={} operator={}", request.getCommandName(), operator);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CliCommandSaveRequest request, String operator) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "ID 不能为空");
        }
        validate(request);
        AgentCliCommand existing = cliCommandService.getById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "CLI 命令不存在");
        }
        if (cliCommandService.existsByCommandName(request.getCommandName(), request.getId())) {
            throw new BusinessException(ResultCode.DATA_DUPLICATED, "命令名已存在");
        }
        converter.updateEntity(request, existing);
        existing.setModifyUser(operator);
        cliCommandService.updateById(existing);
        removeRelated(request.getId());
        saveRelated(request.getId(), request);
        log.info("更新 CLI 命令成功 id={} operator={}", request.getId(), operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String operator) {
        AgentCliCommand existing = cliCommandService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "CLI 命令不存在");
        }
        cliCommandService.removeById(id);
        removeRelated(id);
        projectCliMappingService.removeByCliId(id);
        log.info("删除 CLI 命令 id={} operator={}", id, operator);
    }

    private void validate(CliCommandSaveRequest request) {
        String commandType = request.getCommandType();
        if (!"API".equals(commandType) && !"PAGE".equals(commandType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "命令类型必须为 API 或 PAGE");
        }
        if ("API".equals(commandType)) {
            if (request.getTool() == null || request.getTool().getUrl() == null || request.getTool().getUrl().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "API 型命令的 URL 不能为空");
            }
            if (request.getTool().getMethod() == null || request.getTool().getMethod().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "API 型命令的 Method 不能为空");
            }
        }
        if ("PAGE".equals(commandType)) {
            if (request.getPage() == null || request.getPage().getPageRoute() == null || request.getPage().getPageRoute().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "PAGE 型命令的路由不能为空");
            }
        }
        if (request.getParams() != null) {
            long uniqueCount = request.getParams().stream()
                    .map(p -> p.getParamName())
                    .distinct()
                    .count();
            if (uniqueCount != request.getParams().size()) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "参数名不能重复");
            }
        }
    }

    private void saveRelated(Long cliId, CliCommandSaveRequest request) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            List<AgentCliParam> params = converter.toParamEntityList(request.getParams());
            int sortOrder = 0;
            for (AgentCliParam param : params) {
                param.setCliId(cliId);
                if (param.getSortOrder() == null) {
                    param.setSortOrder(sortOrder++);
                }
            }
            cliParamService.saveBatch(params);
        }
        if ("API".equals(request.getCommandType()) && request.getTool() != null) {
            AgentToolDefinition tool = converter.toToolEntity(request.getTool());
            tool.setCliId(cliId);
            toolDefinitionService.save(tool);
        }
        if ("PAGE".equals(request.getCommandType()) && request.getPage() != null) {
            AgentPageDefinition page = converter.toPageEntity(request.getPage());
            page.setCliId(cliId);
            pageDefinitionService.save(page);
        }
        if (request.getRecallTags() != null && !request.getRecallTags().isEmpty()) {
            List<AgentCliRecallTag> tags = converter.toRecallTagEntityList(request.getRecallTags());
            int sortOrder = 0;
            for (AgentCliRecallTag tag : tags) {
                tag.setCliId(cliId);
                if (tag.getState() == null) {
                    tag.setState(CommonConstants.STATE_ENABLED);
                }
                if (tag.getSortOrder() == null) {
                    tag.setSortOrder(sortOrder++);
                }
            }
            recallTagService.saveBatch(tags);
        }
    }

    private void removeRelated(Long cliId) {
        cliParamService.removeByCliId(cliId);
        toolDefinitionService.removeByCliId(cliId);
        pageDefinitionService.removeByCliId(cliId);
        recallTagService.removeByCliId(cliId);
    }
}
