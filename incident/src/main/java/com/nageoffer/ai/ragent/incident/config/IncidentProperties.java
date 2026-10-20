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

package com.nageoffer.ai.ragent.incident.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "incident")
public class IncidentProperties {

    private Ingress ingress = new Ingress();
    private Map<String, Source> sources = Map.of();
    private Aggregation aggregation = new Aggregation();
    private Retry retry = new Retry();
    private Worker worker = new Worker();
    private Outbox outbox = new Outbox();

    @Data
    public static class Ingress {
        private int maxBodyBytes = 65_536;
        private int maxJsonDepth = 16;
        private Duration signatureSkew = Duration.ofMinutes(5);
    }

    @Data
    public static class Source {
        private List<Key> keys = List.of();
    }

    @Data
    public static class Key {
        private String keyId;
        private String secretEnvironmentVariable;
        private boolean enabled = true;
    }

    @Data
    public static class Aggregation {
        private String ruleVersion = "alert-incident-v1";
        private Duration window = Duration.ofMinutes(30);
    }

    @Data
    public static class Retry {
        private List<Duration> delays = List.of(
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofMinutes(10), Duration.ofMinutes(30));
    }

    @Data
    public static class Worker {
        private boolean enabled = true;
    }

    @Data
    public static class Outbox {
        private boolean enabled = true;
        private Duration maxBackoff = Duration.ofMinutes(15);
    }
}
