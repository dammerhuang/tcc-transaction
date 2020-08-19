package org.mengyun.tcctransaction.spring.support;

import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.recover.RecoverConfig;
import org.mengyun.tcctransaction.repository.CachableTransactionRepository;
import org.mengyun.tcctransaction.spring.recover.DefaultRecoverConfig;
import org.mengyun.tcctransaction.support.TransactionConfigurator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by changmingxie on 11/11/15.
 * 用于Spring的事务配置器
 */
public class SpringTransactionConfigurator implements TransactionConfigurator {

    private static volatile ExecutorService executorService = null;

    /**
     * 这个事务仓库是在使用框架的系统上配置的
     *     <bean id="transactionRepository" class="org.mengyun.tcctransaction.spring.repository.SpringJdbcTransactionRepository">
     *         <property name="dataSource" ref="tccDataSource"/>
     *         <property name="domain" value="CAPITAL"/>
     *         <property name="tbSuffix" value="_CAP"/>
     *     </bean>
     */
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired(required = false)
    private RecoverConfig recoverConfig = DefaultRecoverConfig.INSTANCE;


    private TransactionManager transactionManager;

    /**
     * Spring的事务配置器初始化：
     * 初始化TransactionManager事务管理器
     */
    public void init() {
        transactionManager = new TransactionManager();
        // 设置事务日志仓库
        transactionManager.setTransactionRepository(transactionRepository);

        // 线程池服务
        if (executorService == null) {

            Executors.defaultThreadFactory();
            synchronized (SpringTransactionConfigurator.class) {

                if (executorService == null) {
                    // Terminate 应该就是 Confirm和Cancel的总称
                    executorService = new ThreadPoolExecutor(
                            // 线程池核心线程size
                            recoverConfig.getAsyncTerminateThreadCorePoolSize(),
                            // 线程池最大线程size
                            recoverConfig.getAsyncTerminateThreadMaxPoolSize(),
                            // 存活时间
                            5L,
                            // 时间单位：秒
                            TimeUnit.SECONDS,
                            // 阻塞队列
                            new ArrayBlockingQueue<Runnable>(recoverConfig.getAsyncTerminateThreadWorkQueueSize()),
                            new ThreadFactory() {
                                // 池的编号
                                final AtomicInteger poolNumber = new AtomicInteger(1);
                                // 线程组
                                final ThreadGroup group;
                                // 线程编号
                                final AtomicInteger threadNumber = new AtomicInteger(1);
                                // 线程名称前缀
                                final String namePrefix;

                                {
                                    // 安全管理器
                                    SecurityManager securityManager = System.getSecurityManager();
                                    // 获取线程组
                                    this.group = securityManager != null ? securityManager.getThreadGroup() : Thread.currentThread().getThreadGroup();
                                    // 获取线程名称前缀
                                    this.namePrefix = "tcc-async-terminate-pool-" + poolNumber.getAndIncrement() + "-thread-";
                                }

                                /**
                                 * 获取新线程的方式，直接创建
                                 * @param runnable 线程执行的任务
                                 * @return 返回新生成的线程
                                 */
                                public Thread newThread(Runnable runnable) {
                                    // this.group线程组，一个线程工厂只有一个
                                    // 线程名称 = 名称前缀 + 线程编号
                                    Thread thread = new Thread(this.group, runnable, this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
                                    // 是否守护线程
                                    if (thread.isDaemon()) {
                                        // 如果是，则设置为非守护线程
                                        thread.setDaemon(false);
                                    }

                                    // 优先级都设置为5，正常的优先级就是5，最小1，最大10
                                    if (thread.getPriority() != 5) {
                                        thread.setPriority(5);
                                    }

                                    return thread;
                                }
                            },
                            // CallerRunsPolicy在任务被拒绝添加后，会调用当前线程池的所在的线程去执行被拒绝的任务。
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
        }

        transactionManager.setExecutorService(executorService);

        if (transactionRepository instanceof CachableTransactionRepository) {
            // 如果是一个CachableTransactionRepository或者其子类类型的事务仓库，则设置过期时间
            ((CachableTransactionRepository) transactionRepository).setExpireDuration(recoverConfig.getRecoverDuration());
        }
    }

    @Override
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    @Override
    public RecoverConfig getRecoverConfig() {
        return recoverConfig;
    }
}
