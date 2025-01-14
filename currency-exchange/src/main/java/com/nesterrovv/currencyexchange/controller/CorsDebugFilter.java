//package com.nesterrovv.currencyexchange.controller;
//
//import org.springframework.http.HttpMethod;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.*;
//import reactor.core.publisher.Mono;
//
//@Component
//public class CorsDebugFilter implements WebFilter {
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        ServerHttpRequest request = exchange.getRequest();
//
//
//        System.out.println("CORS Debug: Request from origin: "
//                + request.getHeaders().getOrigin()
//                + ", method: " + request.getMethod());
//
//
//        exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", "*");
//        exchange.getResponse().getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
//        exchange.getResponse().getHeaders().add("Access-Control-Allow-Headers", "*");
//        exchange.getResponse().getHeaders().add("Access-Control-Allow-Credentials", "true");
//
//
//        if (request.getMethod() == HttpMethod.OPTIONS) {
//            exchange.getResponse().setStatusCode(HttpStatus.OK);
//            return exchange.getResponse().setComplete();
//        }
//
//
//        return chain.filter(exchange);
//    }
//}
