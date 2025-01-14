package com.nesterrovv.currencyexchange.service;

import com.nesterrovv.currencyexchange.model.*;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
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

    // для сделок
    private final Sinks.Many<TradeEvent> tradeSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<TradeEvent> tradeFlux = tradeSink.asFlux().share();

    private Flux<StatsData> statsFlux;

    public CurrencyService() {
        // backpressure + троттлинг
        Flux<CurrencyData> usdStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("USD", MEDIAN_USD, AMPLITUDE, phaseUsd, prevUsd, tick))
                .onBackpressureLatest()
                .sample(Duration.ofSeconds(1));

        Flux<CurrencyData> eurStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("EUR", MEDIAN_EUR, AMPLITUDE, phaseEur, prevEur, tick))
                .onBackpressureLatest()
                .sample(Duration.ofSeconds(1));

        Flux<CurrencyData> cnyStream = Flux.interval(Duration.ofMillis(200))
                .map(tick -> generateCurrency("CNY", MEDIAN_CNY, AMPLITUDE, phaseCny, prevCny, tick))
                .onBackpressureLatest()
                .sample(Duration.ofSeconds(1));

        this.currencyFlux = Flux.merge(usdStream, eurStream, cnyStream).share();
        this.orderBookFlux = createAutoOrderBookFlux();
        this.currencyChangedNotificationFlux = createNotificationFlux();
    }

    @PostConstruct
    private void initStatsFlux() {
        this.statsFlux = tradeFlux
                .groupBy(TradeEvent::getCurrency)
                .flatMap(groupedFlux ->
                        groupedFlux
                                .scan(new StatsAccumulator(groupedFlux.key()), (acc, trade) -> {
                                    acc.update(trade);
                                    return acc;
                                })
                                .map(StatsAccumulator::toStatsData)
                )
                .share();
    }

    public Flux<StatsData> getStatsFlux() {
        return statsFlux;
    }

    private Flux<CurrencyChangedNotification> createNotificationFlux() {
        Flux<CurrencyChangedNotification> changeNotifications = currencyFlux
                .filter(currencyData -> Math.abs(currencyData.getChange()) > 5)
                .map(currencyData -> new CurrencyChangedNotification(
                        currencyData.getCurrency(),
                        currencyData.getPrice(),
                        currencyData.getChange()
                ));

        Flux<CurrencyChangedNotification> largeTradeNotifications = tradeFlux
                .filter(trade -> trade.getVolume() >= 1000)
                .map(trade -> new CurrencyChangedNotification(
                        trade.getCurrency(),
                        trade.getPrice(),
                        9999 // Условно, используем как сигнал
                ));

        return Flux.merge(changeNotifications, largeTradeNotifications).share();
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

        double price = median * (1 + amplitude * sin + randomFactor);

        long buyVolumeTotal = userOrders.stream()
                .filter(o -> o.getCurrency().equals(currency) && o.getSide().equalsIgnoreCase("BUY"))
                .mapToLong(o -> (long) o.getVolume())
                .sum();
        long sellVolumeTotal = userOrders.stream()
                .filter(o -> o.getCurrency().equals(currency) && o.getSide().equalsIgnoreCase("SELL"))
                .mapToLong(o -> (long) o.getVolume())
                .sum();

        double activityImpact = (buyVolumeTotal - sellVolumeTotal) * 10;
        price += activityImpact;

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
            double orderVolume = order.getVolume();


            double basePrice = getBasePrice(order.getCurrency());

            // Если пользователь указал конкретную цену, берём её.
            // Если userPrice == null, продолжаем старую логику (basePrice + случайный offset).
            double finalPrice;
            if (order.getUserPrice() != null) {
                finalPrice = order.getUserPrice();
            } else {
                double randomOffset = ThreadLocalRandom.current().nextDouble(-1, 1);
                finalPrice = basePrice + randomOffset;
            }

            if ("BUY".equalsIgnoreCase(order.getSide())) {
                // Частичная логика исполнения
                asks.sort(Comparator.comparingDouble(OrderBook.Order::getPrice));
                int i = 0;
                while (i < asks.size() && orderVolume > 0) {
                    OrderBook.Order currentAsk = asks.get(i);
                    if (currentAsk.getPrice() <= finalPrice) {
                        double availableVol = currentAsk.getVolume();
                        if (availableVol <= orderVolume) {
                            orderVolume -= availableVol;
                            tradeSink.tryEmitNext(new TradeEvent(
                                    order.getCurrency(),
                                    currentAsk.getPrice(),
                                    availableVol,
                                    System.currentTimeMillis()
                            ));
                            asks.remove(i);
                        } else {
                            currentAsk.setVolume(availableVol - orderVolume);
                            tradeSink.tryEmitNext(new TradeEvent(
                                    order.getCurrency(),
                                    currentAsk.getPrice(),
                                    orderVolume,
                                    System.currentTimeMillis()
                            ));
                            orderVolume = 0;
                        }
                    } else {
                        i++;
                    }
                }
                // Остаток (не исполненная часть) уходит в bids
                if (orderVolume > 0) {
                    bids.add(new OrderBook.Order(finalPrice, orderVolume));
                }

            } else {
                // SELL-логика
                bids.sort((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
                int i = 0;
                while (i < bids.size() && orderVolume > 0) {
                    OrderBook.Order currentBid = bids.get(i);
                    if (currentBid.getPrice() >= finalPrice) {
                        double availableVol = currentBid.getVolume();
                        if (availableVol <= orderVolume) {
                            orderVolume -= availableVol;
                            tradeSink.tryEmitNext(new TradeEvent(
                                    order.getCurrency(),
                                    currentBid.getPrice(),
                                    availableVol,
                                    System.currentTimeMillis()
                            ));
                            bids.remove(i);
                        } else {
                            currentBid.setVolume(availableVol - orderVolume);
                            tradeSink.tryEmitNext(new TradeEvent(
                                    order.getCurrency(),
                                    currentBid.getPrice(),
                                    orderVolume,
                                    System.currentTimeMillis()
                            ));
                            orderVolume = 0;
                        }
                    } else {
                        i++;
                    }
                }
                // Остаток уходит в asks
                if (orderVolume > 0) {
                    asks.add(new OrderBook.Order(finalPrice, orderVolume));
                }
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
