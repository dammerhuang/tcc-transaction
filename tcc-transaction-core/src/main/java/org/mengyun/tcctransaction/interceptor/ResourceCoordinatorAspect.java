package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Created by changmingxie on 11/8/15.
 * 资源协调者切面
 */
@Aspect
public abstract class ResourceCoordinatorAspect {

    /**
     * 资源协调拦截器
     */
    private ResourceCoordinatorInterceptor resourceCoordinatorInterceptor;

    @Pointcut("@annotation(org.mengyun.tcctransaction.api.Compensable)")
    public void transactionContextCall() {

    }

    @Around("transactionContextCall()")
    public Object interceptTransactionContextMethod(ProceedingJoinPoint pjp) throws Throwable {
        return resourceCoordinatorInterceptor.interceptTransactionContextMethod(pjp);
    }

    public void setResourceCoordinatorInterceptor(ResourceCoordinatorInterceptor resourceCoordinatorInterceptor) {
        this.resourceCoordinatorInterceptor = resourceCoordinatorInterceptor;
    }

    public abstract int getOrder();
}
