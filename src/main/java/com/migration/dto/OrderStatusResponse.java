package com.migration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderStatusResponse {

    @JsonProperty("order_id")
    private Object orderId;

    private String status;

    public Object getOrderId() {
        return orderId;
    }

    public void setOrderId(Object orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
