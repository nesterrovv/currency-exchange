package com.nesterrovv.currencyexchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyChangedNotification {

    private String currentCurrency;
    private double currentPrice;
    private double percentage;
}
