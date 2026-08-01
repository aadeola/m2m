package com.migration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CreateOrderRequest {

    @JsonProperty("customer_id")
    private Integer customerId;

    @JsonProperty("line_items")
    private List<LineItemRequest> lineItems;

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public List<LineItemRequest> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItemRequest> lineItems) {
        this.lineItems = lineItems;
    }
}
