package com.migration.model.mongo;

import java.math.BigDecimal;

public class EmbeddedLineItem {

    private Integer lineItemId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private EmbeddedProduct product;

    public Integer getLineItemId() {
        return lineItemId;
    }

    public void setLineItemId(Integer lineItemId) {
        this.lineItemId = lineItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public EmbeddedProduct getProduct() {
        return product;
    }

    public void setProduct(EmbeddedProduct product) {
        this.product = product;
    }
}
