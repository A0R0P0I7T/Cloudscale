package com.cloudscale.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAndPropagatesCorrelationIdWhenMissing() {
        MockServerWebExchange exchange = exchange(null);
        AtomicReference<String> forwarded = new AtomicReference<>();

        filter.filter(exchange, request -> {
            forwarded.set(request.getRequest().getHeaders().getFirst("X-Correlation-Id"));
            return reactor.core.publisher.Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(forwarded.get()).matches("[0-9a-f-]{36}");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo(forwarded.get());
    }

    @Test
    void replacesMalformedCorrelationIdAndPreservesValidIds() {
        MockServerWebExchange malformed = exchange("bad id");
        filter.filter(malformed, request -> reactor.core.publisher.Mono.empty()).block(Duration.ofSeconds(1));
        assertThat(malformed.getResponse().getHeaders().getFirst("X-Correlation-Id"))
                .matches("[0-9a-f-]{36}");

        MockServerWebExchange valid = exchange("trace.123-abc");
        filter.filter(valid, request -> reactor.core.publisher.Mono.empty()).block(Duration.ofSeconds(1));
        assertThat(valid.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("trace.123-abc");
    }

    private MockServerWebExchange exchange(String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/v1/auth/login");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        return MockServerWebExchange.from(request.build());
    }
}
