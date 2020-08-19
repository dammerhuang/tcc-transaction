package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());

    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    /**
     * 拦截可补偿的方法
     * @param pjp
     * @return
     * @throws Throwable
     */
    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {

        // 可补偿方法上下文
        CompensableMethodContext compensableMethodContext = new CompensableMethodContext(pjp);

        // 判断当前TCC事务是否激活状态
        boolean isTransactionActive = transactionManager.isTransactionActive();

        // 校验是否合法的事务上下文
        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, compensableMethodContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method " + compensableMethodContext.getMethod().getName());
        }

        // 获取并根据方法角色处理执行该方法
        switch (compensableMethodContext.getMethodRole(isTransactionActive)) {
            case ROOT:
                // 执行根方法
                return rootMethodProceed(compensableMethodContext);
            case PROVIDER:
                // 执行提供者方法
                return providerMethodProceed(compensableMethodContext);
            default:
                // 默认直接执行
                return pjp.proceed();
        }
    }


    private Object rootMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Object returnValue = null;

        Transaction transaction = null;

        // 是否异步确认
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        // 是否异步取消
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        Set<Class<? extends Exception>> allDelayCancelExceptions = new HashSet<Class<? extends Exception>>();
        allDelayCancelExceptions.addAll(this.delayCancelExceptions);
        allDelayCancelExceptions.addAll(Arrays.asList(compensableMethodContext.getAnnotation().delayCancelExceptions()));

        try {
            // 这里就是整个事务的开始！！！
            transaction = transactionManager.begin(compensableMethodContext.getUniqueIdentity());

            try {
                // 执行可补偿方法
                returnValue = compensableMethodContext.proceed();
            } catch (Throwable tryingException) {
                // 报错则判断所报异常是否是DelayCancelException异常的一种
                if (!isDelayCancelException(tryingException, allDelayCancelExceptions)) {

                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s", JSON.toJSONString(transaction)), tryingException);

                    transactionManager.rollback(asyncCancel);
                }
                // 抛出Try异常
                throw tryingException;
            }

            transactionManager.commit(asyncConfirm);

        } finally {
            // 整个方法执行后的清理工作
            transactionManager.cleanAfterCompletion(transaction);
        }

        return returnValue;
    }

    private Object providerMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Transaction transaction = null;

        // 异步确认
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        // 异步取消
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        try {

            switch (TransactionStatus.valueOf(compensableMethodContext.getTransactionContext().getStatus())) {
                case TRYING:
                    transaction = transactionManager.propagationNewBegin(compensableMethodContext.getTransactionContext());
                    return compensableMethodContext.proceed();
                case CONFIRMING:
                    try {
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.commit(asyncConfirm);
                    } catch (NoExistedTransactionException excepton) {
                        //the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:

                    try {
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.rollback(asyncCancel);
                    } catch (NoExistedTransactionException exception) {
                        //the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        Method method = compensableMethodContext.getMethod();

        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable, Set<Class<? extends Exception>> delayCancelExceptions) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                        || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}
