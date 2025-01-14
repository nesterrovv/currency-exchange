package com.nesterrovv.currencyexchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatsData {

    private String currency;
    private double dayHigh;
    private double dayLow;
    private double dayVolume;
}
