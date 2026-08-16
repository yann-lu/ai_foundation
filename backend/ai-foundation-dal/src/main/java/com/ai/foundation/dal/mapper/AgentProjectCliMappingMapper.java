package com.ai.foundation.dal.mapper;

import com.ai.foundation.dal.entity.AgentProjectCliMapping;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentProjectCliMappingMapper extends BaseMapper<AgentProjectCliMapping> {

    @Delete("DELETE FROM agent_project_cli_rel WHERE cli_id = #{cliId}")
    int deleteByCliIdPhysical(Long cliId);

    @Delete("<script>" +
            "DELETE FROM agent_project_cli_rel WHERE project_id = #{projectId} " +
            "AND cli_id IN " +
            "<foreach collection='cliIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteByProjectIdAndCliIdsPhysical(@Param("projectId") Long projectId,
                                           @Param("cliIds") List<Long> cliIds);
}
