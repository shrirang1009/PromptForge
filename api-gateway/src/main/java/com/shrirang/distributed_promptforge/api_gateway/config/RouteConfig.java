package com.shrirang.distributed_promptforge.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("account-auth-route", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9050"))
                .route("account-billing-route-me", r -> r
                        .path("/api/me/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9050"))
                .route("account-billing-route-payments", r -> r
                        .path("/api/payments/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9050"))
                .route("account-billing-route-plans", r -> r
                        .path("/api/plans", "/api/plans/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9050"))
                .route("account-admin-route", r -> r
                        .path("/api/admin/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9050"))
                .route("workspace-route", r -> r
                        .path("/api/projects/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9020"))
                .route("intelligence-route", r -> r
                        .path("/api/chat/**")
                        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}"))
                        .uri("http://localhost:9030"))
                .route("account-billing-route-webhooks", r -> r
                        .path("/webhooks/**")
                        .uri("http://localhost:9050"))
                .build();
    }
}
