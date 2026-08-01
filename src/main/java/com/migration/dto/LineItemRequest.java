package com.migration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LineItemRequest {

    @JsonProperty("product_id")
    private Integer productId;

    private Integer quantity;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
