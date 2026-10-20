/* Licensed under the Apache License, Version 2.0. See LICENSE for details. */
package com.nageoffer.ai.ragent;

import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Consumer;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** IncidentPilot local assembly; keeps the real approval protocol and refuses remote features. */
@Configuration
@Profile("local-demo")
public class LocalDemoConfiguration {
    @Bean
    MessageQueueProducer localDemoClosedProducer() {
        return new MessageQueueProducer() {
            @Override
            public SendResult send(String topic, String keys, String description, Object body) {
                throw new UnsupportedOperationException("LOCAL_DEMO_BROKER_UNAVAILABLE");
            }

            @Override
            public void sendInTransaction(String topic, String keys, String description, Object body,
                    Consumer<Object> localTransaction) {
                throw new UnsupportedOperationException("LOCAL_DEMO_BROKER_UNAVAILABLE");
            }
        };
    }

    @Bean
    FilterRegistrationBean<Filter> localDemoScopeFilter() {
        Filter filter = (request, response, chain) -> {
            var http = (HttpServletRequest) request;
            String raw = http.getRequestURI().substring(http.getContextPath().length());
            String path = http.getServletPath();
            boolean canonical = raw.equals(path) && !raw.contains("%") && !raw.contains("\\")
                    && !raw.contains("//") && java.util.Arrays.stream(raw.split("/"))
                    .noneMatch(part -> part.equals(".") || part.equals(".."));
            boolean auth = "POST".equals(http.getMethod())
                    && (path.equals("/auth/login") || path.equals("/auth/logout"));
            if (canonical && (auth || path.startsWith("/incident/")
                    || ("GET".equals(http.getMethod()) && path.equals("/user/me")))) {
                chain.doFilter(request, response);
                return;
            }
            var output = (HttpServletResponse) response;
            output.setStatus(503);
            output.setContentType("application/json;charset=UTF-8");
            output.getWriter().write("{\"code\":\"LOCAL_DEMO_SCOPE_UNAVAILABLE\","
                    + "\"message\":\"本地演示仅开放事件响应功能\"}");
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-200);
        return registration;
    }
}
