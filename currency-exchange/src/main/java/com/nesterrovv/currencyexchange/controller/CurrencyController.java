package com.nesterrovv.currencyexchange.controller;

import com.nesterrovv.currencyexchange.model.CurrencyData;
import com.nesterrovv.currencyexchange.model.OrderBook;
import com.nesterrovv.currencyexchange.model.UserOrder;
import com.nesterrovv.currencyexchange.service.CurrencyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST-контроллер для SSE (currency, orderbook) + прием заявок.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:80")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    /**
     * SSE-поток курсов валют
     */
    @GetMapping(value = "/currency", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CurrencyData> streamCurrency() {
        System.out.println("HEREERERER");
        return currencyService.getCurrencyFlux();
    }

    /**
     * SSE-поток стакана
     */
    @GetMapping(value = "/orderbook", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderBook> streamOrderBook() {
        return currencyService.getOrderBookFlux();
    }

    /**
     * POST-запрос для выставления заявки
     */
    @PostMapping("/order")
    public Mono<Void> placeOrder(@RequestBody UserOrder userOrder) {
        currencyService.addUserOrder(userOrder);
        // Вернём пустой Mono, т.к. логика добавления — fire-and-forget
        return Mono.empty();
    }
}
