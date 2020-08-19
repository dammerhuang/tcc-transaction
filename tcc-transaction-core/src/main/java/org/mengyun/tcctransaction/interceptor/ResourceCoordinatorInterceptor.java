package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.InvocationContext;
import org.mengyun.tcctransaction.Participant;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.mengyun.tcctransaction.utils.CompensableMethodUtils;
import org.mengyun.tcctransaction.utils.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Created by changmingxie on 11/8/15.
 */
public class ResourceCoordinatorInterceptor {

    private TransactionManager transactionManager;


    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public Object interceptTransactionContextMethod(ProceedingJoinPoint pjp) throws Throwable {

        // 获取外层的transaction对象
        Transaction transaction = transactionManager.getCurrentTransaction();

        if (transaction != null) {

            // 获取事务处于哪个阶段
            switch (transaction.getStatus()) {
                // 原来是在TRYING阶段就注册了参与者了，所以即使这个拦截器所在的切面优先级较低也无所谓
                case TRYING:
                    enlistParticipant(pjp);
                    break;
                case CONFIRMING:
                    break;
                case CANCELLING:
                    break;
            }
        }

        return pjp.proceed(pjp.getArgs());
    }

    /**
     * enlist => 招募，征募
     * @param pjp
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void enlistParticipant(ProceedingJoinPoint pjp) throws IllegalAccessException, InstantiationException {

        // 获取可补偿方法对应的Method对象
        Method method = CompensableMethodUtils.getCompensableMethod(pjp);
        // 校验
        if (method == null) {
            throw new RuntimeException(String.format("join point not found method, point is : %s", pjp.getSignature().getName()));
        }
        // 获取注解类对象
        Compensable compensable = method.getAnnotation(Compensable.class);

        // 获取confirm方法名和cancel方法名
        String confirmMethodName = compensable.confirmMethod();
        String cancelMethodName = compensable.cancelMethod();

        // 获取当前事务对象
        Transaction transaction = transactionManager.getCurrentTransaction();
        // 获取全局事务id并生成分支事务的id然后封装成TransactionXid
        TransactionXid xid = new TransactionXid(transaction.getXid().getGlobalTransactionId());

        if (FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs()) == null) {
            FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().set(new TransactionContext(xid, TransactionStatus.TRYING.getId()), pjp.getTarget(), ((MethodSignature) pjp.getSignature()).getMethod(), pjp.getArgs());
        }

        // 获取到声明这个方法的接口的Class，如果不是接口定义的则返回pjp.getTarget().getClass()本身
        Class targetClass = ReflectionUtils.getDeclaringType(pjp.getTarget().getClass(), method.getName(), method.getParameterTypes());

        // Confirm调用对象
        InvocationContext confirmInvocation = new InvocationContext(targetClass,
                confirmMethodName,
                method.getParameterTypes(), pjp.getArgs());

        // Cancel调用对象
        InvocationContext cancelInvocation = new InvocationContext(targetClass,
                cancelMethodName,
                method.getParameterTypes(), pjp.getArgs());

        // 最开始那个方法其实也是一个Participant（参与者）
        Participant participant =
                new Participant(
                        xid,
                        confirmInvocation,
                        cancelInvocation,
                        compensable.transactionContextEditor());

        transactionManager.enlistParticipant(participant);

    }


}
