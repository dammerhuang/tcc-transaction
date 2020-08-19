package org.mengyun.tcctransaction.sample.dubbo.order.service;

import org.apache.commons.lang3.tuple.Pair;
import org.mengyun.tcctransaction.CancellingException;
import org.mengyun.tcctransaction.ConfirmingException;
import org.mengyun.tcctransaction.sample.order.domain.entity.Order;
import org.mengyun.tcctransaction.sample.order.domain.entity.Shop;
import org.mengyun.tcctransaction.sample.order.domain.repository.ShopRepository;
import org.mengyun.tcctransaction.sample.order.domain.service.OrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by changming.xie on 4/1/16.
 */
@Service
public class PlaceOrderServiceImpl {

    @Autowired
    ShopRepository shopRepository;

    @Autowired
    OrderServiceImpl orderService;

    @Autowired
    PaymentServiceImpl paymentService;

    public String placeOrder(long payerUserId, long shopId, List<Pair<Long, Integer>> productQuantities, final BigDecimal redPacketPayAmount) {
        Shop shop = shopRepository.findById(shopId);

        // 创建订单并保存
        final Order order = orderService.createOrder(payerUserId, shop.getOwnerUserId(), productQuantities);

        Boolean result = false;

        try {

//            ExecutorService executorService = Executors.newFixedThreadPool(2);

//            Future future1 = executorService.submit(new Runnable() {
//                @Override
//                public void run() {
                    // 使用到TCC的方法，付款
                    paymentService.makePayment(order.getMerchantOrderNo(), redPacketPayAmount, order.getTotalAmount().subtract(redPacketPayAmount));
//                }
//            });

//            Future future2 = executorService.submit(new Runnable() {
//                @Override
//                public void run() {
//                    paymentService.makePayment(order.getMerchantOrderNo(), order, redPacketPayAmount, order.getTotalAmount().subtract(redPacketPayAmount));
//                }
//            });
//
//            future1.get();
//            future2.get();

        } catch (ConfirmingException confirmingException) {
            //exception throws with the tcc transaction status is CONFIRMING,
            //when tcc transaction is confirming status,
            // the tcc transaction recovery will try to confirm the whole transaction to ensure eventually consistent.

            result = true;
        } catch (CancellingException cancellingException) {
            //exception throws with the tcc transaction status is CANCELLING,
            //when tcc transaction is under CANCELLING status,
            // the tcc transaction recovery will try to cancel the whole transaction to ensure eventually consistent.
        } catch (Throwable e) {
            //other exceptions throws at TRYING stage.
            //you can retry or cancel the operation.
            e.printStackTrace();
        }

        return order.getMerchantOrderNo();
    }
}
