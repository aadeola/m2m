package com.migration.model.mongo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orders")
public class OrderDocument {

    @Id
    private String id;

    private Integer customerId;
    private LocalDate orderDate;
    private String status;
    private BigDecimal totalAmount;
    private EmbeddedCustomerSummary customer;
    private List<EmbeddedLineItem> lineItems = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public EmbeddedCustomerSummary getCustomer() {
        return customer;
    }

    public void setCustomer(EmbeddedCustomerSummary customer) {
        this.customer = customer;
    }

    public List<EmbeddedLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<EmbeddedLineItem> lineItems) {
        this.lineItems = lineItems;
    }
}
