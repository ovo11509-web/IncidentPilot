/* Licensed under the Apache License, Version 2.0. See LICENSE for details. */
package com.nageoffer.ai.ragent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.config.RocketMQAutoConfiguration;
import com.nageoffer.ai.ragent.framework.mq.producer.DelegatingTransactionListener;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.knowledge.mq.KnowledgeBaseCleanupTransactionChecker;
import com.nageoffer.ai.ragent.knowledge.mq.KnowledgeDocumentChunkTransactionChecker;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;

class LocalDemoConfigurationTest {
    @Test
    void isolatedContextAssemblesExistingTransactionCheckersWithoutOpeningBroker() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("isolated", "local-demo");
            context.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(KnowledgeBaseMapper.class, () -> mock(KnowledgeBaseMapper.class));
            context.registerBean(KnowledgeDocumentMapper.class, () -> mock(KnowledgeDocumentMapper.class));
            // The real application scans this package; @RocketMQTransactionListener is meta-@Component.
            context.scan("com.nageoffer.ai.ragent.framework.mq.producer");
            context.register(LocalDemoConfiguration.class, RocketMQAutoConfiguration.class,
                    KnowledgeBaseCleanupTransactionChecker.class, KnowledgeDocumentChunkTransactionChecker.class);
            context.refresh();
            assertNotNull(context.getBean(DelegatingTransactionListener.class));
            assertEquals(1, context.getBeansOfType(DelegatingTransactionListener.class).size());
            assertNotNull(context.getBean(KnowledgeBaseCleanupTransactionChecker.class));
            assertNotNull(context.getBean(KnowledgeDocumentChunkTransactionChecker.class));
            assertTrue(context.getBeansOfType(RocketMQAutoConfiguration.class).isEmpty());
            var applied = new AtomicBoolean();
            assertThrows(UnsupportedOperationException.class,
                    () -> context.getBean(MessageQueueProducer.class).sendInTransaction(
                            "topic", "key", "test", "body", ignored -> applied.set(true)));
            assertFalse(applied.get());
        }
    }

    @Test
    void localFilterKeepsIncidentAuthenticationRoutesButClosesRemoteFeatures() throws Exception {
        var filter = new LocalDemoConfiguration().localDemoScopeFilter().getFilter();
        for (String path : new String[] {"/auth/login", "/incident/v1/workbench",
                "/incident/internal/v1/action-tickets/consume", "/incident/internal/v1/action-receipts"}) {
            var request = new MockHttpServletRequest("POST", "/api/ragent" + path);
            request.setContextPath("/api/ragent");
            request.setServletPath(path);
            var called = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
            assertTrue(called.get(), path);
        }
        for (String path : new String[] {"/rag/v3/chat", "/knowledge-base", "/users", "/user/me"}) {
            var request = new MockHttpServletRequest("POST", "/api/ragent" + path);
            request.setContextPath("/api/ragent");
            request.setServletPath(path);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("remote feature opened"));
            assertEquals(503, response.getStatus());
            assertTrue(response.getContentAsString().contains("LOCAL_DEMO_SCOPE_UNAVAILABLE"));
        }
        var me = new MockHttpServletRequest("GET", "/api/ragent/user/me");
        me.setContextPath("/api/ragent");
        me.setServletPath("/user/me");
        var called = new AtomicBoolean();
        filter.doFilter(me, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
        for (String raw : new String[] {"/incident/../rag/v3/chat", "/incident/%2e%2e/rag/v3/chat",
                "/incident//v1/workbench", "/incident\\..\\rag/v3/chat"}) {
            var request = new MockHttpServletRequest("POST", "/api/ragent" + raw);
            request.setContextPath("/api/ragent");
            request.setServletPath("/rag/v3/chat");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("path bypass"));
            assertEquals(503, response.getStatus());
        }
        var wrongMethod = new MockHttpServletRequest("GET", "/api/ragent/auth/login");
        wrongMethod.setContextPath("/api/ragent");
        wrongMethod.setServletPath("/auth/login");
        var response = new MockHttpServletResponse();
        filter.doFilter(wrongMethod, response, (req, res) -> fail("auth method bypass"));
        assertEquals(503, response.getStatus());
    }

    @Test
    void closedBrokerCannotPretendToPublishOrApplyTransaction() {
        var producer = new LocalDemoConfiguration().localDemoClosedProducer();
        assertThrows(UnsupportedOperationException.class, () -> producer.send("topic", "key", "test", "body"));
        var applied = new AtomicBoolean();
        assertThrows(UnsupportedOperationException.class, () -> producer.sendInTransaction(
                "topic", "key", "test", "body", ignored -> applied.set(true)));
        assertFalse(applied.get());
    }
}
