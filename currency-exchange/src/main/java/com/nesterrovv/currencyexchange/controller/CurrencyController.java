package com.nesterrovv.currencyexchange.controller;

import com.nesterrovv.currencyexchange.model.*;
import com.nesterrovv.currencyexchange.service.CurrencyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping(value = "/currency", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CurrencyData> streamCurrency() {
        return currencyService.getCurrencyFlux();
    }

    @GetMapping(value = "/orderbook", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderBook> streamOrderBook() {
        return currencyService.getOrderBookFlux();
    }

    @GetMapping(value = "/notification", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CurrencyChangedNotification> streamCurrencyChangedNotification() {
        return currencyService.getCurrencyChangedNotificationFlux();
    }

    @GetMapping(value = "/stats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StatsData> streamStats() {
        return currencyService.getStatsFlux();
    }

    @PostMapping("/order")
    public Mono<Void> placeOrder(@RequestBody UserOrder userOrder) {
        currencyService.addUserOrder(userOrder);
        // логика добавления — fire-and-forget
        return Mono.empty();
    }
}
