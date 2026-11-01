package com.github.randir7.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр для принудительной установки CORS-заголовков.
 *
 * <p>Это "ядерный" вариант, который гарантированно работает в Spring Cloud Gateway,
 * в отличие от CorsWebFilter или globalcors в YAML, которые могут игнорироваться
 * из-за особенностей реактивного роутинга и preflight-запросов.
 *
 * <p>Фильтр имеет наивысший приоритет (HIGHEST_PRECEDENCE), поэтому выполняется
 * ДО любой маршрутизации (lb://user-service и т.д.).
 */
@Component
public class CorsGlobalFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        
        // 1. Принудительное добавление CORS-заголовков в ЛЮБОЙ ответ
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Content-Type");
        headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
        
        // 2. Если это preflight-запрос (OPTIONS), немедленно возвращается 200 OK
        // и прерывается цепочка фильтров, не отправляя запрос дальше в микросервисы.
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }
        
        // 3. Для обычных запросов (GET, POST и т.д.) продолжается цепочка
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        // Наивысший приоритет: выполняется самым первым
        return Ordered.HIGHEST_PRECEDENCE;
    }
}