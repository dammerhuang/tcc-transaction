package org.mengyun.tcctransaction;

import org.mengyun.tcctransaction.api.TransactionXid;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Created by changmingxie on 11/12/15.
 * 事务日志仓库
 */
public interface TransactionRepository {

    /**
     * 创建
     * @param transaction
     * @return
     */
    int create(Transaction transaction);

    /**
     * 更新
     * @param transaction
     * @return
     */
    int update(Transaction transaction);

    /**
     * 删除
     * @param transaction
     * @return
     */
    int delete(Transaction transaction);

    /**
     * 根据xid获取事务日志
     * @param xid
     * @return
     */
    Transaction findByXid(TransactionXid xid);

    List<Transaction> findAllUnmodifiedSince(Date date);
}
