package com.nesterrovv.currencyexchange.service;

import com.nesterrovv.currencyexchange.model.StatsData;
import com.nesterrovv.currencyexchange.model.TradeEvent;

public class StatsAccumulator {

    private final String currency;
    private double maxPrice = Double.MIN_VALUE;
    private double minPrice = Double.MAX_VALUE;
    private double totalVolume = 0.0;

    public StatsAccumulator(String currency) {
        this.currency = currency;
    }

    public void update(TradeEvent trade) {
        double price = trade.getPrice();
        double volume = trade.getVolume();
        if (price > maxPrice) maxPrice = price;
        if (price < minPrice) minPrice = price;
        totalVolume += volume;
    }

    public StatsData toStatsData() {
        StatsData stats = new StatsData();
        stats.setCurrency(currency);
        stats.setDayHigh(maxPrice);
        stats.setDayLow(minPrice);
        stats.setDayVolume(totalVolume);
        return stats;
    }

}
