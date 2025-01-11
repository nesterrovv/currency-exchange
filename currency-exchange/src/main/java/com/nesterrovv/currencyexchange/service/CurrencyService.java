package com.nesterrovv.currencyexchange.service;

import com.nesterrovv.currencyexchange.model.CurrencyChangedNotification;
import com.nesterrovv.currencyexchange.model.CurrencyData;
import com.nesterrovv.currencyexchange.model.OrderBook;
import com.nesterrovv.currencyexchange.model.UserOrder;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CurrencyService {

    private final Flux<CurrencyData> currencyFlux;
    private Flux<OrderBook> orderBookFlux;
    private Flux<CurrencyChangedNotification> currencyChangedNotificationFlux;

    private final ConcurrentLinkedQueue<UserOrder> userOrders = new ConcurrentLinkedQueue<>();

    private static final double MEDIAN_USD = 80;
    private static final double MEDIAN_EUR = 85;
    private static final double MEDIAN_CNY = 11;

    private static final double AMPLITUDE = 0.15;
    private static final double OMEGA = 0.05;

    private final double phaseUsd = Math.random() * 2 * Math.PI;
    private final double phaseEur = Math.random() * 2 * Math.PI;
    private final double phaseCny = Math.random() * 2 * Math.PI;

    private final AtomicReference<Double> prevUsd = new AtomicReference<>(MEDIAN_USD);
    private final AtomicReference<Double> prevEur = new AtomicReference<>(MEDIAN_EUR);
    private final AtomicReference<Double> prevCny = new AtomicReference<>(MEDIAN_CNY);

    private boolean autoGenerateOrderBook = true;

    public CurrencyService() {
        Flux<CurrencyData> usdStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("USD", MEDIAN_USD, AMPLITUDE, phaseUsd, prevUsd, tick));
        Flux<CurrencyData> eurStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("EUR", MEDIAN_EUR, AMPLITUDE, phaseEur, prevEur, tick));
        Flux<CurrencyData> cnyStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("CNY", MEDIAN_CNY, AMPLITUDE, phaseCny, prevCny, tick));

        this.currencyFlux = Flux.merge(usdStream, eurStream, cnyStream).share();
        this.orderBookFlux = createAutoOrderBookFlux();
        this.currencyChangedNotificationFlux = createNotificationFlux();
    }

    private Flux<CurrencyChangedNotification> createNotificationFlux() {
        return currencyFlux.filter(currencyData -> Math.abs(currencyData.getChange()) > 5)
                .map(currencyData -> new CurrencyChangedNotification(
                        currencyData.getCurrency(),
                        currencyData.getPrice(),
                        currencyData.getChange()
                )).share();
    }

    private CurrencyData generateCurrency(
            String currency,
            double median,
            double amplitude,
            double phase,
            AtomicReference<Double> prevRef,
            Long tick
    ) {
        double time = tick.doubleValue();
        double sin = Math.sin(OMEGA * time + phase);
        double randomFactor = ThreadLocalRandom.current().nextDouble(-0.02, 0.02);

        // Базовая цена с учётом синусоиды и случайности
        double price = median * (1 + amplitude * sin + randomFactor);

        // Влияние активности в стакане
        long buyCount = userOrders.stream().filter(o -> o.getCurrency().equals(currency) && o.getSide().equalsIgnoreCase("BUY")).count();
        long sellCount = userOrders.stream().filter(o -> o.getCurrency().equals(currency) && o.getSide().equalsIgnoreCase("SELL")).count();

        // Новый коэффициент влияния
        double activityImpact = (buyCount - sellCount) * 4; // Сильное влияние разницы покупок/продаж
        price += activityImpact;

        // Логирование для отладки
        System.out.printf("Currency: %s | BuyCount: %d | SellCount: %d | ActivityImpact: %.2f | New Price: %.2f%n",
                currency, buyCount, sellCount, activityImpact, price);

        // Вычисляем процент изменения цены
        double oldPrice = prevRef.getAndSet(price);
        double changePct = (price - oldPrice) / oldPrice * 100;

        return new CurrencyData(currency, price, System.currentTimeMillis(), changePct);
    }


    private Flux<OrderBook> createAutoOrderBookFlux() {
        return Flux.interval(Duration.ofMillis(500))
                .filter(tick -> autoGenerateOrderBook)
                .map(tick -> {
                    List<OrderBook.Order> bids = generateRandomBids();
                    List<OrderBook.Order> asks = generateRandomAsks();
                    processUserOrders(bids, asks);
                    return new OrderBook(bids, asks);
                })
                .share();
    }

    public void setAutoGenerateOrderBook(boolean autoGenerate) {
        this.autoGenerateOrderBook = autoGenerate;
    }

    public void addUserOrder(UserOrder order) {
        userOrders.offer(order);
    }

    public Flux<CurrencyData> getCurrencyFlux() {
        return currencyFlux;
    }

    public Flux<OrderBook> getOrderBookFlux() {
        return orderBookFlux;
    }

    public Flux<CurrencyChangedNotification> getCurrencyChangedNotificationFlux() {
        return currencyChangedNotificationFlux;
    }

    public OrderBook generateManualOrderBook() {
        List<OrderBook.Order> bids = generateRandomBids();
        List<OrderBook.Order> asks = generateRandomAsks();
        processUserOrders(bids, asks);
        return new OrderBook(bids, asks);
    }

    private void processUserOrders(List<OrderBook.Order> bids, List<OrderBook.Order> asks) {
        while (!userOrders.isEmpty()) {
            UserOrder order = userOrders.poll();
            double basePrice = getBasePrice(order.getCurrency());
            double randomOffset = ThreadLocalRandom.current().nextDouble(-1, 1);
            double price = basePrice + randomOffset;
            OrderBook.Order newOrder = new OrderBook.Order(price, order.getVolume());
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                bids.add(newOrder);
            } else {
                asks.add(newOrder);
            }
        }
        bids.sort((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
        asks.sort((o1, o2) -> Double.compare(o1.getPrice(), o2.getPrice()));
    }

    private List<OrderBook.Order> generateRandomBids() {
        List<OrderBook.Order> bids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double price = MEDIAN_USD - i - ThreadLocalRandom.current().nextDouble(0.5);
            double volume = ThreadLocalRandom.current().nextDouble(10, 100);
            bids.add(new OrderBook.Order(price, volume));
        }
        return bids;
    }

    private List<OrderBook.Order> generateRandomAsks() {
        List<OrderBook.Order> asks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double price = MEDIAN_USD + i + ThreadLocalRandom.current().nextDouble(0.5);
            double volume = ThreadLocalRandom.current().nextDouble(10, 100);
            asks.add(new OrderBook.Order(price, volume));
        }
        return asks;
    }

    private double getBasePrice(String currency) {
        return switch (currency) {
            case "USD" -> MEDIAN_USD;
            case "EUR" -> MEDIAN_EUR;
            case "CNY" -> MEDIAN_CNY;
            default -> MEDIAN_USD;
        };
    }
}
