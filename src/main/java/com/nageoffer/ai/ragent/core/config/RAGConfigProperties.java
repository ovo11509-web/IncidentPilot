package com.nageoffer.ai.ragent.core.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class RAGConfigProperties {

    @Value("${rag.query-rewrite.enabled:true}")
    private Boolean queryRewriteEnabled;
}
