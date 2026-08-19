package com.ai.foundation.mediator.agent.event;

import com.ai.foundation.com.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Run 取消协作标记。
 *
 * <p>写入到 Redis 后，任意副本里的 {@code ReactCancelModelHook.interrupt()} 都能读到，
 * 解决当前项目里仅靠 {@code Sinks.Empty<Void>} 流级取消的局限（无法中断框架内部图循环）。
 *
 * <p>key 形如 {@code ai:foundation:run:cancel:<runCode>}，TTL 默认 1 小时，
 * 防止漏删导致下一次同 runCode 的 Run 被误判。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunCancelFlagStore {

    /** 默认 TTL 1 小时。Run 正常应在分钟级内结束，超过即视为残留。 */
    static final long DEFAULT_TTL_SECONDS = 3600L;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 标记 Run 已被取消。
     *
     * @param runCode Run 编码
     */
    public void markCancelled(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.runCancelFlag(runCode.trim()),
                    "1",
                    DEFAULT_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("RunCancelFlagStore markCancelled failed, runCode={}", runCode, ex);
        }
    }

    /**
     * 判断 Run 是否已被取消（多副本安全）。
     *
     * @param runCode Run 编码
     * @return true 已取消
     */
    public boolean isCancelled(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                    RedisKeyConstants.runCancelFlag(runCode.trim())));
        } catch (Exception ex) {
            // Redis 异常时按未取消处理：宁可让 Run 多跑几轮，也不要因为标记丢失而误中断。
            return false;
        }
    }

    /**
     * 清理取消标记。Run 进入终态（完成 / 失败 / 取消）后调用，避免下次 Run 复用了同 runCode 时的残留。
     *
     * @param runCode Run 编码
     */
    public void clear(String runCode) {
        if (StringUtils.isBlank(runCode)) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyConstants.runCancelFlag(runCode.trim()));
        } catch (Exception ex) {
            log.warn("RunCancelFlagStore clear failed, runCode={}", runCode, ex);
        }
    }
}
