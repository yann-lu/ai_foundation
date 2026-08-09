package com.ai.foundation.mediator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 会话摘要异步执行线程池配置。
 *
 * <p>使用独立线程池处理增量摘要任务，避免占用主业务线程。
 * 摘要任务是 I/O 密集型（等待 LLM 响应），核心线程数保持较低。
 */
@Configuration
public class SummaryAsyncConfig {

    public static final String SUMMARY_EXECUTOR = "summaryAsyncExecutor";

    @Bean(name = SUMMARY_EXECUTOR)
    public Executor summaryAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("summary-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
