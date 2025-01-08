package com.nesterrovv.currencyexchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBook {

    private List<Order> bids;
    private List<Order> asks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private double price;
        private double volume;
    }

}