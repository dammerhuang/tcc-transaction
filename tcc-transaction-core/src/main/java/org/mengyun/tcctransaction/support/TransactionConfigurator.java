package org.mengyun.tcctransaction.support;

import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.recover.RecoverConfig;

/**
 * Created by changming.xie on 2/24/17.
 */
public interface TransactionConfigurator {

    /**
     * 事务管理器
     * @return
     */
    TransactionManager getTransactionManager();

    /**
     * 事务仓库
     * @return
     */
    TransactionRepository getTransactionRepository();

    /**
     * 恢复配置
     * @return
     */
    RecoverConfig getRecoverConfig();
}
