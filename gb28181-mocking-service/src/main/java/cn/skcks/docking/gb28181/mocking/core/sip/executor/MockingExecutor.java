package cn.skcks.docking.gb28181.mocking.core.sip.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Order(1)
@EnableAsync(
        proxyTargetClass = true
)
public class MockingExecutor{
    public static final int CPU_NUM = Runtime.getRuntime().availableProcessors();
    public static final int MAX_POOL_SIZE;
    private static final int KEEP_ALIVE_TIME = 30;
    public static final int TASK_NUM = 10000;
    public static final String THREAD_NAME_PREFIX = "mocking-executor";
    public static final String EXECUTOR_BEAN_NAME = "mockingTaskExecutor";

    public MockingExecutor() {
    }

    @Bean(EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor sipTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CPU_NUM * 2);
        executor.setMaxPoolSize(1000);
        executor.setQueueCapacity(10000);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    static {
        MAX_POOL_SIZE = CPU_NUM * 2;
    }
}
