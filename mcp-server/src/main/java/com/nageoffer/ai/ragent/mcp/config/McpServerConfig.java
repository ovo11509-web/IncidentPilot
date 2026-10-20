/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.mcp.config;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * MCP Server 配置类
 */
@Configuration
@Profile("!local-demo")
public class McpServerConfig {
    /** Reviewed read-only implementations; tool annotations alone never grant ordinary-route access. */
    private static final Set<String> ORDINARY_READ_ONLY = Set.of(
            "current_date", "weather_query", "youcom_search", "asset_query",
            "query_after_sale", "query_cart", "query_coupons", "leave_query",
            "query_logistics", "meeting_room_query", "sales_query", "ticket_query",
            "query_order", "search_product");

    @Bean
    public HttpServletStreamableServerTransportProvider transportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            @Qualifier("transportProvider") HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean
    public McpSyncServer mcpServer(
            @Qualifier("transportProvider") HttpServletStreamableServerTransportProvider transportProvider,
                                   List<McpServerFeatures.SyncToolSpecification> toolSpecs) {
        var allowed = ordinaryTools(toolSpecs);
        return McpServer.sync(transportProvider)
                .serverInfo("ragent-mcp-server", "0.0.1")
                .tools(allowed)
                .build();
    }

    public static List<McpServerFeatures.SyncToolSpecification> ordinaryTools(
            List<McpServerFeatures.SyncToolSpecification> offered) {
        var seen = new HashSet<String>();
        for (var spec : offered) {
            if (!seen.add(spec.tool().name())) throw new IllegalStateException("duplicate MCP tool name");
        }
        return offered.stream().filter(spec -> ORDINARY_READ_ONLY.contains(spec.tool().name())
                && spec.tool().annotations() != null
                && Boolean.TRUE.equals(spec.tool().annotations().readOnlyHint())).toList();
    }

    /**
     * 工具必须自报是读还是写：调用方按 readOnlyHint 决定要不要拦下来让用户确认
     */
    static void requireReadOnlyHint(List<McpServerFeatures.SyncToolSpecification> toolSpecs) {
        List<String> undeclared = toolSpecs.stream()
                .filter(spec -> spec.tool().annotations() == null
                        || spec.tool().annotations().readOnlyHint() == null)
                .map(spec -> spec.tool().name())
                .toList();
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException(
                    "以下 MCP 工具未声明 readOnlyHint，请在 annotations 里显式写明只读或写操作: " + undeclared);
        }
    }
}
