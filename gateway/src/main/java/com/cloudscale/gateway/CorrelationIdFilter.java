package com.cloudscale.gateway;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {
    static final String HEADER_NAME = "X-Correlation-Id";
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String correlationId = supplied != null && VALID_CORRELATION_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();
        ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();
        correlatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        return chain.filter(correlatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
