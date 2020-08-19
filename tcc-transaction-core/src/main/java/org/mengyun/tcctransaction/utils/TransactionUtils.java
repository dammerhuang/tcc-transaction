package org.mengyun.tcctransaction.utils;

import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.interceptor.CompensableMethodContext;

/**
 * Created by changming.xie on 2/23/17.
 */
public class TransactionUtils {

    public static boolean isLegalTransactionContext(boolean isTransactionActive, CompensableMethodContext compensableMethodContext) {

        // MANDATORY => 强制
        /**
         * Spring的PROPAGATION_MANDATORY代表当前必须存在一个外层事务，否则抛异常，
         * 这里应该也是模仿Spring，
         * 判断逻辑为：如果是Propagation.MANDATORY传播类型的 && 当前事务非激活状态 && 事务上下文为空
         */
        if (compensableMethodContext.getPropagation().equals(Propagation.MANDATORY) && !isTransactionActive && compensableMethodContext.getTransactionContext() == null) {
            return false;
        }

        return true;
    }
}
