package org.mengyun.tcctransaction.sample.dubbo.order.service;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.UniqueIdentity;
import org.mengyun.tcctransaction.sample.dubbo.capital.api.CapitalTradeOrderService;
import org.mengyun.tcctransaction.sample.dubbo.capital.api.dto.CapitalTradeOrderDto;
import org.mengyun.tcctransaction.sample.dubbo.redpacket.api.RedPacketTradeOrderService;
import org.mengyun.tcctransaction.sample.dubbo.redpacket.api.dto.RedPacketTradeOrderDto;
import org.mengyun.tcctransaction.sample.order.domain.entity.Order;
import org.mengyun.tcctransaction.sample.order.domain.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Calendar;

/**
 * Created by changming.xie on 4/1/16.
 */
@Service
public class PaymentServiceImpl {

    @Autowired
    CapitalTradeOrderService capitalTradeOrderService;

    @Autowired
    RedPacketTradeOrderService redPacketTradeOrderService;

    @Autowired
    OrderRepository orderRepository;

    /**
     * 主业务服务
     * @param orderNo
     * @param redPacketPayAmount
     * @param capitalPayAmount
     */
    @Compensable(confirmMethod = "confirmMakePayment", cancelMethod = "cancelMakePayment", asyncConfirm = false, delayCancelExceptions = {SocketTimeoutException.class, org.apache.dubbo.remoting.TimeoutException.class})
    public void makePayment(@UniqueIdentity String orderNo, BigDecimal redPacketPayAmount, BigDecimal capitalPayAmount) {
        // 这里还是用log打印吧......
        System.out.println("order try make payment called.time seq:" + DateFormatUtils.format(Calendar.getInstance(), "yyyy-MM-dd HH:mm:ss"));

        Order order = orderRepository.findByMerchantOrderNo(orderNo);
        //check if the order status is DRAFT, if no, means that another call makePayment for the same order happened, ignore this call makePayment.
        // 实现幂等
        if (order.getStatus().equals("DRAFT")) {
            // 将order状态改为PAYING：支付中
            order.pay(redPacketPayAmount, capitalPayAmount);
            try {
                orderRepository.updateOrder(order);
            } catch (OptimisticLockingFailureException e) {
                //ignore the concurrently update order exception, ensure idempotency.
            }
        }

        // 资金账户操作
        String result = capitalTradeOrderService.record(buildCapitalTradeOrderDto(order));
        // 红包账户操作
        String result2 = redPacketTradeOrderService.record(buildRedPacketTradeOrderDto(order));
    }

    public void confirmMakePayment(String orderNo, BigDecimal redPacketPayAmount, BigDecimal capitalPayAmount) {


        try {
            Thread.sleep(1000l);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("order confirm make payment called. time seq:" + DateFormatUtils.format(Calendar.getInstance(), "yyyy-MM-dd HH:mm:ss"));

        Order foundOrder = orderRepository.findByMerchantOrderNo(orderNo);

        //check if the trade order status is PAYING, if no, means another call confirmMakePayment happened, return directly, ensure idempotency.
        if (foundOrder != null && foundOrder.getStatus().equals("PAYING")) {
            foundOrder.confirm();
            orderRepository.updateOrder(foundOrder);
        }
    }

    public void cancelMakePayment(String orderNo,  BigDecimal redPacketPayAmount, BigDecimal capitalPayAmount) {

        try {
            Thread.sleep(1000l);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("order cancel make payment called.time seq:" + DateFormatUtils.format(Calendar.getInstance(), "yyyy-MM-dd HH:mm:ss"));

        Order foundOrder = orderRepository.findByMerchantOrderNo(orderNo);

        //check if the trade order status is PAYING, if no, means another call cancelMakePayment happened, return directly, ensure idempotency.
        if (foundOrder != null && foundOrder.getStatus().equals("PAYING")) {
            foundOrder.cancelPayment();
            orderRepository.updateOrder(foundOrder);
        }
    }


    private CapitalTradeOrderDto buildCapitalTradeOrderDto(Order order) {

        CapitalTradeOrderDto tradeOrderDto = new CapitalTradeOrderDto();
        tradeOrderDto.setAmount(order.getCapitalPayAmount());
        tradeOrderDto.setMerchantOrderNo(order.getMerchantOrderNo());
        tradeOrderDto.setSelfUserId(order.getPayerUserId());
        tradeOrderDto.setOppositeUserId(order.getPayeeUserId());
        tradeOrderDto.setOrderTitle(String.format("order no:%s", order.getMerchantOrderNo()));

        return tradeOrderDto;
    }

    private RedPacketTradeOrderDto buildRedPacketTradeOrderDto(Order order) {
        RedPacketTradeOrderDto tradeOrderDto = new RedPacketTradeOrderDto();
        tradeOrderDto.setAmount(order.getRedPacketPayAmount());
        tradeOrderDto.setMerchantOrderNo(order.getMerchantOrderNo());
        tradeOrderDto.setSelfUserId(order.getPayerUserId());
        tradeOrderDto.setOppositeUserId(order.getPayeeUserId());
        tradeOrderDto.setOrderTitle(String.format("order no:%s", order.getMerchantOrderNo()));

        return tradeOrderDto;
    }
}
