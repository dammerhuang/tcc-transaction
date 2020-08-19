package org.mengyun.tcctransaction.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 *
 * @author changmingxie
 * @date 10/25/15
 * Compensable => 可补偿的，说明TCC是补偿型的
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Compensable {

    public Propagation propagation() default Propagation.REQUIRED;

    public String confirmMethod() default "";

    public String cancelMethod() default "";

    public Class<? extends TransactionContextEditor> transactionContextEditor() default DefaultTransactionContextEditor.class;

    public Class<? extends Exception>[] delayCancelExceptions() default {};

    public boolean asyncConfirm() default false;

    public boolean asyncCancel() default false;

    class NullableTransactionContextEditor implements TransactionContextEditor {

        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            return null;
        }

        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

        }
    }

    /**
     * 默认事务上下文编辑器
     */
    class DefaultTransactionContextEditor implements TransactionContextEditor {

        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            // 获取事务上下文参数在方法中的参数位置
            int position = getTransactionContextParamPosition(method.getParameterTypes());

            // 如果position>=0，说明方法是有这个参数的，所以返回事务上下文对象，否则null
            if (position >= 0) {
                return (TransactionContext) args[position];
            }

            return null;
        }

        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {
            // 获取事务上下文在方法中的参数位置并将传入的transactionContext设置进去
            int position = getTransactionContextParamPosition(method.getParameterTypes());
            if (position >= 0) {
                args[position] = transactionContext;
            }
        }

        /**
         * 获取事务上下文参数的位置（position）
         * @param parameterTypes 方法的所有参数类型
         * @return 事务上下文参数的position
         */
        public static int getTransactionContextParamPosition(Class<?>[] parameterTypes) {

            int position = -1;

            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i].equals(org.mengyun.tcctransaction.api.TransactionContext.class)) {
                    position = i;
                    break;
                }
            }
            return position;
        }

        public static TransactionContext getTransactionContextFromArgs(Object[] args) {

            TransactionContext transactionContext = null;

            for (Object arg : args) {
                if (arg != null && org.mengyun.tcctransaction.api.TransactionContext.class.isAssignableFrom(arg.getClass())) {

                    transactionContext = (org.mengyun.tcctransaction.api.TransactionContext) arg;
                }
            }

            return transactionContext;
        }
    }
}