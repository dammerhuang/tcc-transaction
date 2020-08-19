package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

/**
 * Created by changmingxie on 10/26/15.
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());

    private TransactionRepository transactionRepository;

    /**
     * CURRENT存着整个分布式事务的所有事务日志对象
     */
    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<Deque<Transaction>>();

    private ExecutorService executorService;

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public TransactionManager() {


    }

    public Transaction begin(Object uniqueIdentify) {
        Transaction transaction = new Transaction(uniqueIdentify,TransactionType.ROOT);
        transactionRepository.create(transaction);
        registerTransaction(transaction);
        return transaction;
    }

    public Transaction begin() {
        Transaction transaction = new Transaction(TransactionType.ROOT);
        transactionRepository.create(transaction);
        registerTransaction(transaction);
        return transaction;
    }

    public Transaction propagationNewBegin(TransactionContext transactionContext) {

        Transaction transaction = new Transaction(transactionContext);
        transactionRepository.create(transaction);

        registerTransaction(transaction);
        return transaction;
    }

    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }

    public void commit(boolean asyncCommit) {

        // 获取当前事务对象
        final Transaction transaction = getCurrentTransaction();

        // 将事务对象的状态设置为正在确认
        transaction.changeStatus(TransactionStatus.CONFIRMING);

        // 更新之
        transactionRepository.update(transaction);

        // 异步确认
        if (asyncCommit) {
            try {
                // commit开始时间
                Long statTime = System.currentTimeMillis();

                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        commitTransaction(transaction);
                    }
                });
                logger.debug("async submit cost time:" + (System.currentTimeMillis() - statTime));
            } catch (Throwable commitException) {
                logger.warn("compensable transaction async submit confirm failed, recovery job will try to confirm later.", commitException);
                throw new ConfirmingException(commitException);
            }
        } else {
            // 同步commit
            commitTransaction(transaction);
        }
    }


    public void rollback(boolean asyncRollback) {

        final Transaction transaction = getCurrentTransaction();
        // 取出需要Cancel的事务对象，将其状态设置问CANCELLING
        transaction.changeStatus(TransactionStatus.CANCELLING);

        transactionRepository.update(transaction);

        // 异步回滚
        if (asyncRollback) {

            try {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        rollbackTransaction(transaction);
                    }
                });
            } catch (Throwable rollbackException) {
                logger.warn("compensable transaction async rollback failed, recovery job will try to rollback later.", rollbackException);
                throw new CancellingException(rollbackException);
            }
        } else {
            // 同步回滚
            rollbackTransaction(transaction);
        }
    }


    private void commitTransaction(Transaction transaction) {
        try {
            // 整个事务commit，其实就是调用所有参与者的commit方法
            transaction.commit();
            // 删除数据库对应事务记录和缓存里对应的事务对象
            transactionRepository.delete(transaction);
        } catch (Throwable commitException) {
            // 如果commit过程报错则抛出Confirm异常
            logger.warn("compensable transaction confirm failed, recovery job will try to confirm later.", commitException);
            throw new ConfirmingException(commitException);
        }
    }

    private void rollbackTransaction(Transaction transaction) {
        try {
            // 这个rollback其实就是将所有参与者都执行rollback
            transaction.rollback();
            // 删掉对应数据库的事务记录和缓存里面的事务对象
            transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {
            // 如果回滚报错则对外抛出Cancel异常
            logger.warn("compensable transaction rollback failed, recovery job will try to rollback later.", rollbackException);
            throw new CancellingException(rollbackException);
        }
    }

    /**
     * 获取当前事务
     * @return 事务对象
     */
    public Transaction getCurrentTransaction() {
        if (isTransactionActive()) {
            // 最顶部的出栈
            return CURRENT.get().peek();
        }
        return null;
    }

    /**
     * 事务是否激活的判断依据是当前线程本地对象是否有事务日志集合
     * @return 线程是否激活
     */
    public boolean isTransactionActive() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }


    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<Transaction>());
        }

        CURRENT.get().push(transaction);
    }

    public void cleanAfterCompletion(Transaction transaction) {
        // 如果当前事务处于激活状态 && 事务对象不为空
        if (isTransactionActive() && transaction != null) {
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                CURRENT.get().pop();
                if (CURRENT.get().size() == 0) {
                    CURRENT.remove();
                }
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }

    public void enlistParticipant(Participant participant) {
        Transaction transaction = this.getCurrentTransaction();
        transaction.enlistParticipant(participant);
        transactionRepository.update(transaction);
    }
}
