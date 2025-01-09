package com.nesterrovv.currencyexchange.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorsDebugFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        System.out.println("CORS Debug: Request from " + exchange.getRequest().getHeaders().getOrigin());
        return chain.filter(exchange);
    }
}
