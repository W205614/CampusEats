package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.BaseException;
import com.sky.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserExperienceRegressionTest {
    @Mock EmployeeMapper employeeMapper;
    @Mock DishMapper dishMapper;
    @Mock SetmealMapper setmealMapper;
    @Mock ShoppingCartMapper shoppingCartMapper;
    @Mock OrderMapper orderMapper;
    @Mock AddressBookMapper addressBookMapper;
    @Mock com.sky.service.ReportService reportService;
    @Mock RedisTemplate redisTemplate;
    @Mock ValueOperations values;
    @InjectMocks EmployeeServiceImpl employees;
    @InjectMocks DishServiceImpl dishes;
    @InjectMocks SetmealServiceImpl setmeals;
    @InjectMocks ShoppingCartServiceImpl cart;
    @InjectMocks OrderServiceImpl orders;
    @InjectMocks com.sky.controller.admin.ReportController reports;

    @BeforeEach void setup() { BaseContext.setCurrentId(7L); }
    @AfterEach void cleanup() { BaseContext.removeCurrentId(); }
    @Test void missingEmployeeHasUsefulError() {
        assertEquals("员工不存在", assertThrows(BaseException.class, () -> employees.getById(999L)).getMessage());
    }
    @Test void missingDishHasUsefulError() {
        assertEquals("菜品不存在", assertThrows(BaseException.class, () -> dishes.getByIdWithFlavor(999L)).getMessage());
    }
    @Test void missingSetmealHasUsefulError() {
        assertEquals("套餐不存在", assertThrows(BaseException.class, () -> setmeals.getByIdWithDish(999L)).getMessage());
    }
    @Test void invalidPageDoesNotReachDatabase() {
        DishPageQueryDTO dto = new DishPageQueryDTO(); dto.setPage(0); dto.setPageSize(0);
        assertThrows(BaseException.class, () -> dishes.pageQuery(dto));
        verifyNoInteractions(dishMapper);
    }
    @Test void emptyCartSelectionIsRejected() {
        assertThrows(BaseException.class, () -> cart.addShoppingCart(new ShoppingCartDTO()));
        verifyNoInteractions(shoppingCartMapper);
    }
    @Test void cannotAddStoppedDish() {
        when(dishMapper.getById(8L)).thenReturn(Dish.builder().id(8L).status(0).build());
        ShoppingCartDTO dto = new ShoppingCartDTO(); dto.setDishId(8L);
        assertThrows(BaseException.class, () -> cart.addShoppingCart(dto));
        verifyNoInteractions(shoppingCartMapper);
    }
    @Test void closedShopCannotCreateOrder() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenReturn(0);
        assertThrows(BaseException.class, () -> orders.submitOrder(new OrdersSubmitDTO()));
        verifyNoInteractions(orderMapper);
    }
    @Test void completedOrderCannotBeReopenedByConfirm() {
        OrdersConfirmDTO dto = new OrdersConfirmDTO(); dto.setId(88L);
        when(orderMapper.transition(any(), eq(Orders.TO_BE_CONFIRMED))).thenReturn(0);
        assertThrows(BaseException.class, () -> orders.confirm(dto));
        verify(orderMapper, never()).update(any());
    }
    @Test void merchantCanCancelUnpaidOrderWithoutGeneratingEmptyUpdate() throws Exception {
        when(orderMapper.getById(88L)).thenReturn(Orders.builder().id(88L).status(1).payStatus(0).build());
        when(orderMapper.transition(any(), eq(1))).thenReturn(1);
        OrdersCancelDTO dto = new OrdersCancelDTO(); dto.setId(88L); dto.setCancelReason("test");
        orders.cancel(dto);
        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).transition(update.capture(), eq(1));
        assertEquals(Orders.CANCELLED, update.getValue().getStatus());
        assertNull(update.getValue().getPayStatus());
        assertEquals(88L, update.getValue().getId());
    }
    @Test void wrongOldPasswordCannotChangeCredentials() {
        when(employeeMapper.getById(7L)).thenReturn(Employee.builder().id(7L)
                .password(DigestUtils.md5DigestAsHex("123456".getBytes())).build());
        EmployeePasswordDTO dto = new EmployeePasswordDTO(); dto.setOldPassword("incorrect"); dto.setNewPassword("NewPass789");
        assertThrows(BaseException.class, () -> employees.editPassword(dto));
        verify(employeeMapper, never()).update(any());
    }
    @Test void passwordUpdateUsesAuthenticatedEmployeeOnly() {
        when(employeeMapper.getById(7L)).thenReturn(Employee.builder().id(7L)
                .password(DigestUtils.md5DigestAsHex("123456".getBytes())).build());
        EmployeePasswordDTO dto = new EmployeePasswordDTO(); dto.setOldPassword("123456"); dto.setNewPassword("NewPass789");
        employees.editPassword(dto);
        ArgumentCaptor<Employee> update = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).update(update.capture());
        assertEquals(7L, update.getValue().getId());
        assertEquals(DigestUtils.md5DigestAsHex("NewPass789".getBytes()), update.getValue().getPassword());
    }
    @Test void missingReportDatesAreRejectedWithoutDatabaseCalls() {
        assertThrows(BaseException.class, () -> reports.turnoverStatistics(null, null));
        verifyNoInteractions(reportService);
    }
    @Test void reversedReportDatesAreRejectedWithoutDatabaseCalls() {
        java.time.LocalDate today = java.time.LocalDate.now();
        assertThrows(BaseException.class, () -> reports.orderStatistics(today, today.minusDays(1)));
        verifyNoInteractions(reportService);
    }
    private OrdersSubmitDTO checkout() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenReturn(1);
        when(addressBookMapper.getById(3L)).thenReturn(AddressBook.builder().id(3L).userId(7L).build());
        org.springframework.test.util.ReflectionTestUtils.setField(orders, "deliveryCheckEnabled", false);
        when(shoppingCartMapper.list(any())).thenReturn(java.util.Collections.singletonList(
                ShoppingCart.builder().dishId(8L).number(1).amount(new java.math.BigDecimal("10.00")).build()));
        OrdersSubmitDTO dto = new OrdersSubmitDTO(); dto.setAddressBookId(3L); return dto;
    }
    @Test void staleCartPriceRequiresExplicitReselection() {
        OrdersSubmitDTO dto = checkout();
        when(dishMapper.getById(8L)).thenReturn(Dish.builder().id(8L).status(1).price(new java.math.BigDecimal("12.00")).build());
        assertTrue(assertThrows(BaseException.class, () -> orders.submitOrder(dto)).getMessage().contains("价格已调整"));
        verifyNoInteractions(orderMapper);
        verify(shoppingCartMapper, never()).deleteByUserId(anyLong());
    }
    @Test void existingCartCannotCheckoutStoppedDish() {
        OrdersSubmitDTO dto = checkout();
        when(dishMapper.getById(8L)).thenReturn(Dish.builder().id(8L).status(0).build());
        assertTrue(assertThrows(BaseException.class, () -> orders.submitOrder(dto)).getMessage().contains("已停售"));
        verifyNoInteractions(orderMapper);
    }
}
