package org.mengyun.tcctransaction.api;

import java.lang.reflect.Method;

/**
 * Created by changming.xie on 1/18/17.
 * 事务上下文编辑器
 */
public interface TransactionContextEditor {

    /**
     * 获取事务上下文
     * @param target
     * @param method
     * @param args
     * @return
     */
    public TransactionContext get(Object target, Method method, Object[] args);

    /**
     * 设置事务上下文
     * @param transactionContext
     * @param target
     * @param method
     * @param args
     */
    public void set(TransactionContext transactionContext, Object target, Method method, Object[] args);

}
