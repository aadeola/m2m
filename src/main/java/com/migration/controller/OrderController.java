package com.migration.controller;

import com.migration.dto.CreateOrderRequest;
import com.migration.dto.OrderResponse;
import com.migration.dto.OrderStatusResponse;
import com.migration.service.OrderService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(value = "customer_id", required = false) Integer customerId) {
        if (customerId != null) {
            return orderService.getOrdersByCustomerId(customerId);
        }
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}/status")
    public OrderStatusResponse getOrderStatus(@PathVariable String id) {
        return orderService.getOrderStatus(id);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return orderService.getOrderById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }
}
