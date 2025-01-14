package com.nesterrovv.currencyexchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель для фиксации сделки (trade) при исполнении ордеров.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeEvent {
    private String currency;
    private double price;
    private double volume;
    private long timestamp;
}
