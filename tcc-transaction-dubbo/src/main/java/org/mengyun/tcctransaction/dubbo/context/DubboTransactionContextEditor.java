package org.mengyun.tcctransaction.dubbo.context;

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.RpcContext;
import com.alibaba.fastjson.JSON;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.dubbo.constants.TransactionContextConstants;

import java.lang.reflect.Method;

/**
 * Created by changming.xie on 1/19/17.
 */
public class DubboTransactionContextEditor implements TransactionContextEditor {
    @Override
    public TransactionContext get(Object target, Method method, Object[] args) {
        // 获取当前RPC线程的上下文并从上下文的attachments中获取key为TRANSACTION_CONTEXT的事务上下文的json字符串
        String context = RpcContext.getContext().getAttachment(TransactionContextConstants.TRANSACTION_CONTEXT);

        // 将json字符串解析成TransactionContext对象
        if (StringUtils.isNotEmpty(context)) {
            return JSON.parseObject(context, TransactionContext.class);
        }

        return null;
    }

    @Override
    public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {
        // 将事务上下文序列化成json字符串然后设置进当前的rpc上下文的attachments附件里面去，key为TRANSACTION_CONTEXT
        RpcContext.getContext().setAttachment(TransactionContextConstants.TRANSACTION_CONTEXT, JSON.toJSONString(transactionContext));
    }
}
