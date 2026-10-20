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

package com.nageoffer.ai.ragent.incident.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class IncidentPropertiesTest {

    @Test
    void bindsSourceReferencesAndOperationalSwitchesWithoutSecretValues() {
        var source = new MapConfigurationPropertySource(Map.of(
                "incident.sources.demo.keys[0].key-id", "current",
                "incident.sources.demo.keys[0].secret-environment-variable", "INCIDENT_DEMO_KEY",
                "incident.worker.enabled", "false"));
        var properties = new Binder(source).bind("incident", Bindable.of(IncidentProperties.class)).get();

        assertEquals("current", properties.getSources().get("demo").getKeys().get(0).getKeyId());
        assertEquals("INCIDENT_DEMO_KEY", properties.getSources().get("demo").getKeys().get(0)
                .getSecretEnvironmentVariable());
        assertFalse(properties.getWorker().isEnabled());
        assertTrue(properties.getOutbox().isEnabled());
        assertEquals(Duration.ofMinutes(30), properties.getAggregation().getWindow());
        assertEquals(5, properties.getRetry().getDelays().size());
    }
}
