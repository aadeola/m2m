package com.migration.controller;

import com.migration.dto.CustomerResponse;
import com.migration.dto.OrderResponse;
import com.migration.service.CustomerService;
import com.migration.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final OrderService orderService;

    public CustomerController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @GetMapping
    public List<CustomerResponse> getCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable String id) {
        return customerService.getCustomerById(id);
    }

    @GetMapping("/{id}/orders")
    public List<OrderResponse> getCustomerOrders(@PathVariable("id") String customerId) {
        return orderService.getOrdersByCustomerId(Integer.parseInt(customerId));
    }
}
