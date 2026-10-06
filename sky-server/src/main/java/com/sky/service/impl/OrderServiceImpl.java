package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;

    @Value("${sky.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    @Value("${sky.delivery.check-enabled:true}")
    private boolean deliveryCheckEnabled;
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate redisTemplate;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        if (!Integer.valueOf(1).equals(redisTemplate.opsForValue().get("SHOP_STATUS"))) {
            throw new OrderBusinessException("店铺已打烊，请在营业时间下单");
        }
        // 处理各种业务异常(地址簿为空, 购物车数据为空)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        if (!BaseContext.getCurrentId().equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException("无权使用该收货地址");
        }

        // 检查用户的收货地址是否超出配送范围
        if (deliveryCheckEnabled) {
            checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
        }

        // 查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders, "tablewareNumber", "packAmount");
        orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null ? 0 : ordersSubmitDTO.getTablewareNumber());
        // Match the supplied mini-program's pricing: dish total + 6 delivery + 1/item packaging.
        // Calculate from server-side cart data rather than trusting the client's amount.
        BigDecimal dishTotal = BigDecimal.ZERO;
        int itemCount = 0;
        for (ShoppingCart cart : shoppingCartList) {
            BigDecimal currentPrice;
            if (cart.getDishId() != null) {
                Dish dish = dishMapper.getById(cart.getDishId());
                if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) {
                    throw new OrderBusinessException("购物车中的菜品已停售，请调整后再下单");
                }
                currentPrice = dish.getPrice();
            } else if (cart.getSetmealId() != null) {
                Setmeal setmeal = setmealMapper.getById(cart.getSetmealId());
                if (setmeal == null || !Integer.valueOf(1).equals(setmeal.getStatus())) {
                    throw new OrderBusinessException("购物车中的套餐已停售，请调整后再下单");
                }
                currentPrice = setmeal.getPrice();
            } else throw new ShoppingCartBusinessException("购物车数据异常");
            if (cart.getNumber() == null || cart.getNumber() <= 0 || cart.getAmount() == null
                    || cart.getAmount().signum() < 0) {
                throw new ShoppingCartBusinessException("购物车数据异常");
            }
            if (currentPrice == null || cart.getAmount().compareTo(currentPrice) != 0) {
                throw new OrderBusinessException("商品价格已调整，请清空购物车后重新选择");
            }
            itemCount = Math.addExact(itemCount, cart.getNumber());
            dishTotal = dishTotal.add(cart.getAmount().multiply(BigDecimal.valueOf(cart.getNumber())));
        }
        orders.setPackAmount(itemCount);
        orders.setAmount(dishTotal.add(BigDecimal.valueOf(6L + itemCount)));
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(System.currentTimeMillis() + java.util.UUID.randomUUID().toString().substring(0, 8));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        // 向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for(ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail(); // 订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        // 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        if (!mockPaymentEnabled) {
            throw new OrderBusinessException("真实微信支付尚未接通，请勿将本地模拟支付用于生产");
        }
        Long userId = BaseContext.getCurrentId();
        Orders orders = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (orders == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        if (Orders.PAID.equals(orders.getPayStatus())) return new OrderPaymentVO();
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        int changed = orderMapper.markMockPaid(orders.getId(), userId, LocalDateTime.now());
        if (changed == 0) {
            Orders current = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
            if (current != null && Orders.PAID.equals(current.getPayStatus())) return new OrderPaymentVO();
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map = new HashMap();
        map.put("type", 1);// 消息类型，1表示来单提醒
        map.put("orderId", orders.getId());
        map.put("content", "订单号：" + ordersPaymentDTO.getOrderNumber());

        // 通过WebSocket实现来单提醒，向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        log.info("来单提醒：{}", JSON.toJSONString(map));

        return new OrderPaymentVO();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 用户端历史订单查询
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQueryUser(int pageNum, int pageSize, Integer status) {
        com.sky.utils.PageValidation.check(pageNum, pageSize);
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();

        // 查询出订单明细, 封装到OrderVO响应
        if(page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId(); // 获取订单id

                // 查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(),list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);
        if (orders == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);

        // 查询订单对应的菜品或套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将订单和详细信息封装到OrderVO返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) throws Exception{
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if(ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        requireUserOrder(ordersDB);
        if (Orders.CANCELLED.equals(ordersDB.getStatus())) return;

        // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if(ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消, 需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 调用微信支付退款接口
            /*weChatPayUtil.refund(
                    ordersDB.getNumber(), // 商户订单号
                    ordersDB.getNumber(), // 商户退款单号
                    new BigDecimal(0.01), // 退款金额, 单位 元
                    new BigDecimal(0.01)); // 原订单金额*/

            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态, 取消原因, 取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        applyTransition(orders, ordersDB.getStatus());
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        requireUserOrder(orderMapper.getById(id));

        // 根据id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        com.sky.utils.PageValidation.check(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态, 需要额外返回订单菜品信息, 将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息, 自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if(!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 手动添加地址
                orderVO.setAddress(getAddress(orders.getAddressBookId()));

                // 将订单菜品信息封装到orderVO中, 并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    private String getAddress(Long addressBookId){
        AddressBook addressBook = addressBookMapper.getById(addressBookId);
        if (addressBook == null) {
            return "";
        }
        String address = addressBook.getProvinceName() + addressBook.getCityName() +
                addressBook.getDistrictName() + addressBook.getDetail();
        return address;
    }

    /**
     * 各个状态的订单查询
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态, 分别查询待接单, 待派送, 派送中的订单数量
        // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        applyTransition(orders, Orders.TO_BE_CONFIRMED);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO dto) throws Exception {
        Orders current = orderMapper.getById(dto.getId());
        if (current == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        if (!Orders.TO_BE_CONFIRMED.equals(current.getStatus())) throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        Orders update = Orders.builder().id(current.getId()).status(Orders.CANCELLED)
                .rejectionReason(dto.getRejectionReason()).cancelTime(LocalDateTime.now()).build();
        if (Orders.PAID.equals(current.getPayStatus())) update.setPayStatus(Orders.REFUND);
        applyTransition(update, current.getStatus());
    }

    /** 商家取消，仅允许未支付、待接单和已接单状态。退款仍为本地模拟。 */
    public void cancel(OrdersCancelDTO dto) throws Exception {
        Orders current = orderMapper.getById(dto.getId());
        if (current == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        if (Orders.CANCELLED.equals(current.getStatus())) return;
        if (current.getStatus() < Orders.PENDING_PAYMENT || current.getStatus() > Orders.CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = Orders.builder().id(current.getId()).status(Orders.CANCELLED)
                .cancelReason(dto.getCancelReason()).cancelTime(LocalDateTime.now()).build();
        if (Orders.PAID.equals(current.getPayStatus())) update.setPayStatus(Orders.REFUND);
        applyTransition(update, current.getStatus());
    }
    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在, 状态是否为3
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态, 改为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        applyTransition(orders, ordersDB.getStatus());
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在, 状态是否为4
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态, 改为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        applyTransition(orders, ordersDB.getStatus());
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 100000){
            //配送距离超过100000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 用户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        requireUserOrder(ordersDB);

        // 校验订单是否存在
        if(ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号: " + ordersDB.getNumber());

        // 通过websocket向客户端浏览器推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    private void applyTransition(Orders update, Integer expectedStatus) {
        if (orderMapper.transition(update, expectedStatus) != 1) {
            throw new OrderBusinessException("订单不存在或状态已变化，请刷新后重试");
        }
    }
    private void requireUserOrder(Orders order) {
        if (order == null || !BaseContext.getCurrentId().equals(order.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    }
}
