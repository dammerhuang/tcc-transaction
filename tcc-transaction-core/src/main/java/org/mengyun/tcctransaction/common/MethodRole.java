package org.mengyun.tcctransaction.common;

/**
 * Created by changmingxie on 11/11/15.
 * 方法角色
 */
public enum MethodRole {
    /**
     * 根，开启事务的主业务方法
     */
    ROOT,
    CONSUMER,
    /**
     * 提供者，被主业务方法调用的从业务服务方法
     */
    PROVIDER,
    NORMAL;
}
