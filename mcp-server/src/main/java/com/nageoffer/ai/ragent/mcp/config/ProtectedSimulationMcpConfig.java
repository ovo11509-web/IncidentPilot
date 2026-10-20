package com.nageoffer.ai.ragent.mcp.config;

import com.nageoffer.ai.ragent.mcp.simulation.ProtectedServiceAuthentication;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Distinct transport and executable registry for the single approved simulation command. */
@Configuration
public class ProtectedSimulationMcpConfig {
    @Bean
    public HttpServletStreamableServerTransportProvider protectedTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .contextExtractor(request -> io.modelcontextprotocol.common.McpTransportContext.create(java.util.Map.of(
                        "incident.observation", com.nageoffer.ai.ragent.mcp.simulation.McpObservation.capture())))
                .mcpEndpoint("/mcp/incident").build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> protectedMcpServlet(
            @Qualifier("protectedTransportProvider") HttpServletStreamableServerTransportProvider transport) {
        return new ServletRegistrationBean<>(transport, "/mcp/incident");
    }

    @Bean
    public FilterRegistrationBean<Filter> protectedMcpAuthenticationFilter(
            ProtectedServiceAuthentication authentication) {
        Filter filter = (request, response, chain) -> {
            var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
            var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            boolean authenticated = authentication.isAuthenticated(httpRequest.getHeader("Authorization"));
            var observation = com.nageoffer.ai.ragent.mcp.simulation.McpObservation.root("incident.http",
                    io.opentelemetry.api.trace.SpanKind.SERVER,
                    com.nageoffer.ai.ragent.mcp.simulation.McpObservation.extract(httpRequest, authenticated));
            observation.id("requestId", com.nageoffer.ai.ragent.mcp.simulation.McpObservation.requestId(httpRequest));
            httpResponse.setHeader("X-Request-Id", observation.fields().get("requestId"));
            if (observation.fields().containsKey("traceId")) httpResponse.setHeader("X-Trace-Id", observation.fields().get("traceId"));
            try {
                if (!authenticated) { observation.outcome("UNAUTHORIZED"); httpResponse.sendError(401); return; }
                chain.doFilter(request, response);
            } finally {
                if (request.isAsyncStarted()) {
                    observation.detach();
                    try { request.getAsyncContext().addListener(new jakarta.servlet.AsyncListener() {
                        public void onComplete(jakarta.servlet.AsyncEvent event) { observation.outcome("HTTP_COMPLETE"); observation.close(); }
                        public void onTimeout(jakarta.servlet.AsyncEvent event) { observation.outcome("HTTP_TIMEOUT"); observation.close(); }
                        public void onError(jakarta.servlet.AsyncEvent event) { observation.outcome("HTTP_ERROR"); observation.close(); }
                        public void onStartAsync(jakarta.servlet.AsyncEvent event) { event.getAsyncContext().addListener(this); }
                    }); } catch (IllegalStateException completed) { observation.close(); }
                } else { observation.close(); }
            }
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/mcp/incident", "/mcp/incident/*");
        registration.setOrder(-100);
        return registration;
    }

    @Bean
    public McpSyncServer protectedMcpServer(
            @Qualifier("protectedTransportProvider") HttpServletStreamableServerTransportProvider transport,
            @Qualifier("protectedSimulationSpecification") McpServerFeatures.SyncToolSpecification simulation) {
        if (!"simulation.set-state".equals(simulation.tool().name())) {
            throw new IllegalStateException("invalid protected MCP registry");
        }
        return McpServer.sync(transport).serverInfo("incident-simulation", "1")
                .tools(simulation).build();
    }
}
