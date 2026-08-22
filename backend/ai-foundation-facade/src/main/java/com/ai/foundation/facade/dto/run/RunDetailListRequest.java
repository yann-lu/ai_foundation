package com.ai.foundation.facade.dto.run;

import lombok.Data;

/**
 * 按会话拉取 Run 详情列表的请求参数。
 *
 * <p>返回 PageResult&lt;RunDetailResponse&gt;，每条都包含 requestMessages / reply / reasoning / tasks，
 * 用于前端"详情"页一次性加载一个会话下所有轮次的完整快照。
 */
@Data
public class RunDetailListRequest {

    /** 会话编码。 */
    private String conversationCode;

    /** 页码，从 1 开始。 */
    private Long current = 1L;

    /** 单页大小。 */
    private Long size = 50L;
}
