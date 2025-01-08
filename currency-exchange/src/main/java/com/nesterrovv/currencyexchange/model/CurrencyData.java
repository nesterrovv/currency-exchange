package com.nesterrovv.currencyexchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyData {

    private String currency;
    private double price;
    private long timestamp;
    private double change;

}
