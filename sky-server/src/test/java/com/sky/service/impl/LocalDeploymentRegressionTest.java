package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.vo.OrderSubmitVO;
import java.math.BigDecimal;
import java.util.Collections;
import org.mockito.ArgumentCaptor;
import com.sky.entity.AddressBook;
import com.sky.entity.Orders;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class LocalDeploymentRegressionTest {
    @Mock OrderMapper orderMapper;
    @Mock AddressBookMapper addressBookMapper;
    @Mock WebSocketServer webSocketServer;
    @Mock ShoppingCartMapper shoppingCartMapper;
    @Mock OrderDetailMapper orderDetailMapper;
    @Mock org.springframework.data.redis.core.RedisTemplate redisTemplate;
    @Mock org.springframework.data.redis.core.ValueOperations valueOperations;
    @Mock com.sky.mapper.DishMapper dishMapper;
    @InjectMocks OrderServiceImpl orders;
    @InjectMocks AddressBookServiceImpl addresses;

    @BeforeEach void prepare() {
        BaseContext.setCurrentId(7L);
        ReflectionTestUtils.setField(orders, "mockPaymentEnabled", true);
    }
    @AfterEach void cleanup() { BaseContext.removeCurrentId(); }
    private OrdersPaymentDTO payment() {
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("original-order");
        return dto;
    }
    private Orders unpaid() {
        return Orders.builder().id(11L).number("original-order").userId(7L)
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
    }
    @Test void paysRequestedOrderWithoutPriorSubmitInThisProcess() throws Exception {
        when(orderMapper.getByNumberAndUserId("original-order", 7L)).thenReturn(unpaid());
        when(orderMapper.markMockPaid(eq(11L), eq(7L), any(LocalDateTime.class))).thenReturn(1);
        assertNotNull(orders.payment(payment()));
        verify(orderMapper).markMockPaid(eq(11L), eq(7L), any(LocalDateTime.class));
        verify(webSocketServer).sendToAllClient(contains("\"orderId\":11"));
    }
    @Test void repeatPaymentDoesNotUpdateOrNotifyAgain() throws Exception {
        Orders paid = unpaid();
        paid.setStatus(Orders.TO_BE_CONFIRMED);
        paid.setPayStatus(Orders.PAID);
        when(orderMapper.getByNumberAndUserId("original-order", 7L)).thenReturn(paid);
        orders.payment(payment());
        verify(orderMapper, never()).markMockPaid(anyLong(), anyLong(), any());
        verifyNoInteractions(webSocketServer);
    }
    @Test void concurrentPaymentLoserReturnsSuccessWithoutSecondNotification() throws Exception {
        Orders paid = unpaid();
        paid.setPayStatus(Orders.PAID);
        when(orderMapper.getByNumberAndUserId("original-order", 7L)).thenReturn(unpaid(), paid);
        when(orderMapper.markMockPaid(eq(11L), eq(7L), any())).thenReturn(0);
        assertNotNull(orders.payment(payment()));
        verifyNoInteractions(webSocketServer);
    }
    @Test void missingOrOtherUsersOrderCannotBePaid() {
        assertThrows(OrderBusinessException.class, () -> orders.payment(payment()));
        verify(orderMapper, never()).markMockPaid(anyLong(), anyLong(), any());
    }
    @Test void cancelledOrderCannotBePaid() {
        Orders cancelled = unpaid();
        cancelled.setStatus(Orders.CANCELLED);
        when(orderMapper.getByNumberAndUserId("original-order", 7L)).thenReturn(cancelled);
        assertThrows(OrderBusinessException.class, () -> orders.payment(payment()));
        verifyNoInteractions(webSocketServer);
    }
    @Test void mockPaymentIsDisabledOutsideExplicitLocalConfiguration() {
        ReflectionTestUtils.setField(orders, "mockPaymentEnabled", false);
        assertThrows(OrderBusinessException.class, () -> orders.payment(payment()));
        verifyNoInteractions(orderMapper);
    }
    @Test void rejectsOtherUsersCancellation() {
        Orders foreign = unpaid();
        foreign.setUserId(99L);
        when(orderMapper.getById(11L)).thenReturn(foreign);
        assertThrows(OrderBusinessException.class, () -> orders.userCancelById(11L));
        verify(orderMapper, never()).update(any());
    }
    @Test void rejectsOtherUsersAddressBeforeModification() {
        AddressBook foreign = AddressBook.builder().id(3L).userId(99L).build();
        when(addressBookMapper.getById(3L)).thenReturn(foreign);
        assertThrows(AddressBookBusinessException.class, () -> addresses.deleteById(3L));
        verify(addressBookMapper, never()).deleteById(anyLong());
    }
    @Test void recalculatesAmountAndPackagingFromServerCart() {
        ReflectionTestUtils.setField(orders, "deliveryCheckEnabled", false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("SHOP_STATUS")).thenReturn(1);
        when(addressBookMapper.getById(3L)).thenReturn(AddressBook.builder().id(3L).userId(7L).build());
        ShoppingCart cart = ShoppingCart.builder().dishId(42L).number(2).amount(new BigDecimal("6.00")).build();
        when(dishMapper.getById(42L)).thenReturn(com.sky.entity.Dish.builder().id(42L).status(1).price(new BigDecimal("6.00")).build());
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(cart));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(3L);
        dto.setAmount(new BigDecimal("0.01"));
        dto.setPackAmount(0);
        OrderSubmitVO result = orders.submitOrder(dto);
        assertEquals(0, new BigDecimal("20.00").compareTo(result.getOrderAmount()));
        ArgumentCaptor<Orders> captured = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captured.capture());
        assertEquals(2, captured.getValue().getPackAmount());
        assertEquals(7L, captured.getValue().getUserId());
    }
}
