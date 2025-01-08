package com.nesterrovv.currencyexchange.service;

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

    // Потоки данных о валюте:
    private final Flux<CurrencyData> currencyFlux;
    private final Flux<OrderBook> orderBookFlux;

    // Очередь для «пользовательских» заявок
    private final ConcurrentLinkedQueue<UserOrder> userOrders = new ConcurrentLinkedQueue<>();

    // Медианные курсы (пример):
    private static final double MEDIAN_USD = 80;
    private static final double MEDIAN_EUR = 85;
    private static final double MEDIAN_CNY = 11;

    // Параметры синусоид
    private static final double AMPLITUDE_USD = 0.15;
    private static final double AMPLITUDE_EUR = 0.15;
    private static final double AMPLITUDE_CNY = 0.15;
    private static final double OMEGA = 0.05; // скорость колебаний

    // Фаза (разные для каждой валюты)
    private final double phaseUsd = Math.random() * 2 * Math.PI;
    private final double phaseEur = Math.random() * 2 * Math.PI;
    private final double phaseCny = Math.random() * 2 * Math.PI;

    // Предыдущие цены, чтобы считать % change:
    private final AtomicReference<Double> prevUsd = new AtomicReference<>(MEDIAN_USD);
    private final AtomicReference<Double> prevEur = new AtomicReference<>(MEDIAN_EUR);
    private final AtomicReference<Double> prevCny = new AtomicReference<>(MEDIAN_CNY);

    public CurrencyService() {
        // Генерация валют: USD, EUR, CNY
        Flux<CurrencyData> usdStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("USD", MEDIAN_USD, AMPLITUDE_USD, phaseUsd, prevUsd, tick));
        Flux<CurrencyData> eurStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("EUR", MEDIAN_EUR, AMPLITUDE_EUR, phaseEur, prevEur, tick));
        Flux<CurrencyData> cnyStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("CNY", MEDIAN_CNY, AMPLITUDE_CNY, phaseCny, prevCny, tick));

        this.currencyFlux = Flux.merge(usdStream, eurStream, cnyStream)
                .share();

        // Генерация стакана каждые 500 мс
        this.orderBookFlux = Flux.interval(Duration.ofMillis(500))
                .map(tick -> {
                    // Сгенерировать bids / asks случайным образом
                    List<OrderBook.Order> randomBids = generateRandomBids();
                    List<OrderBook.Order> randomAsks = generateRandomAsks();

                    // Учесть пользовательские ордера
                    List<OrderBook.Order> userBids = new ArrayList<>();
                    List<OrderBook.Order> userAsks = new ArrayList<>();

                    // «Извлекаем» все накопившиеся ордера из очереди
                    while (!userOrders.isEmpty()) {
                        UserOrder userOrder = userOrders.poll();
                        if (userOrder != null) {
                            // Выберем «примерную» цену (текущую медианную? или случайно?)
                            double basePrice = getBasePrice(userOrder.getCurrency());
                            double randomOffset = ThreadLocalRandom.current().nextDouble(-1, 1);
                            double price = basePrice + randomOffset;  // упрощённо

                            // Формируем OrderBook.Order
                            OrderBook.Order order = new OrderBook.Order(price, userOrder.getVolume());

                            if ("BUY".equalsIgnoreCase(userOrder.getSide())) {
                                userBids.add(order);
                            } else {
                                userAsks.add(order);
                            }
                        }
                    }

                    // Теперь объединим случайные и пользовательские
                    randomBids.addAll(userBids);
                    randomAsks.addAll(userAsks);

                    // В реальном стакане сортируем bids по убыванию цены, asks по возрастанию
                    randomBids.sort((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
                    randomAsks.sort((o1, o2) -> Double.compare(o1.getPrice(), o2.getPrice()));

                    return new OrderBook(randomBids, randomAsks);
                })
                .share();
    }

    /**
     * Сгенерировать данные о валюте по синусоиде.
     */
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
        double price = median * (1 + amplitude * sin);

        double oldPrice = prevRef.getAndSet(price);
        double changePct = (price - oldPrice) / oldPrice * 100;

        return new CurrencyData(currency, price, System.currentTimeMillis(), changePct);
    }

    /**
     * Добавить пользовательский ордер в очередь.
     */
    public void addUserOrder(UserOrder order) {
        userOrders.offer(order);
    }

    /**
     * Вернуть поток данных о валюте.
     */
    public Flux<CurrencyData> getCurrencyFlux() {
        return currencyFlux;
    }

    /**
     * Вернуть поток стакана.
     */
    public Flux<OrderBook> getOrderBookFlux() {
        return orderBookFlux;
    }

    /**
     * Вспомогательный метод для определения "базовой" цены в зависимости от валюты.
     */
    private double getBasePrice(String currency) {
        return switch (currency) {
            case "USD" -> MEDIAN_USD;
            case "EUR" -> MEDIAN_EUR;
            case "CNY" -> MEDIAN_CNY;
            default -> 80.0;
        };
    }

    /**
     * Генерация случайных bids.
     */
    private List<OrderBook.Order> generateRandomBids() {
        List<OrderBook.Order> bids = new ArrayList<>();
        for(int i = 0; i < 5; i++) {
            double bidPrice = 80.0 - i - ThreadLocalRandom.current().nextDouble(0.5);
            double bidVolume = ThreadLocalRandom.current().nextDouble(10, 100);
            bids.add(new OrderBook.Order(bidPrice, bidVolume));
        }
        return bids;
    }

    /**
     * Генерация случайных asks.
     */
    private List<OrderBook.Order> generateRandomAsks() {
        List<OrderBook.Order> asks = new ArrayList<>();
        for(int i = 0; i < 5; i++) {
            double askPrice = 80.0 + i + ThreadLocalRandom.current().nextDouble(0.5);
            double askVolume = ThreadLocalRandom.current().nextDouble(10, 100);
            asks.add(new OrderBook.Order(askPrice, askVolume));
        }
        return asks;
    }

}
