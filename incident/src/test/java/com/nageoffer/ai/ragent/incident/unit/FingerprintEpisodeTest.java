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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.service.AlertFingerprint;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FingerprintEpisodeTest {
    private final AlertV1Canonicalizer canonicalizer = new AlertV1Canonicalizer(new IncidentProperties());

    @Test
    void fingerprintUsesOnlyIdentityTupleAndKeepsResourceIdCase() {
        var fingerprint = new AlertFingerprint();
        var first = fingerprint.of("source", alert("Prod", "HOST-1", "HIGH", "CPU high"));
        var equivalent = fingerprint.of("source", alert("prod", "HOST-1", "LOW", "different title"));
        var differentResource = fingerprint.of("source", alert("prod", "host-1", "HIGH", "CPU high"));
        assertEquals(first, equivalent);
        assertNotEquals(first, differentResource);
        assertEquals("alert-incident-v1:" + first.digest(), first.aggregationKey());
        assertEquals(64, first.digest().length());
    }

    @Test
    void exactWindowIsIncludedAndFailedMembersRemainInPartition() {
        var t = OffsetDateTime.parse("2026-09-23T00:00:00Z");
        var points = List.of(
                new EpisodePartitioner.AlertPoint("3", t.plusMinutes(60).plusNanos(1000), "RECEIVED"),
                new EpisodePartitioner.AlertPoint("1", t, "FAILED"),
                new EpisodePartitioner.AlertPoint("2", t.plusMinutes(30), "PROCESSED"));
        var episodes = new EpisodePartitioner().partition(points, List.of(), Duration.ofMinutes(30));
        assertEquals(2, episodes.size());
        assertEquals(List.of("1", "2"), episodes.get(0).members().stream().map(EpisodePartitioner.AlertPoint::id).toList());
        assertEquals("1", episodes.get(0).anchor().id());
        assertEquals("3", episodes.get(1).anchor().id());
    }

    @Test
    void lifecycleSealSplitsEvenWhenGapIsInsideWindow() {
        var t = OffsetDateTime.parse("2026-09-23T00:00:00Z");
        var points = List.of(
                new EpisodePartitioner.AlertPoint("2", t.plusSeconds(2), "FAILED"),
                new EpisodePartitioner.AlertPoint("1", t, "PROCESSED"),
                new EpisodePartitioner.AlertPoint("3", t.plusSeconds(3), "RECEIVED"));
        var episodes = new EpisodePartitioner().partition(points, List.of(t.plusSeconds(2)), Duration.ofMinutes(30));
        assertEquals(2, episodes.size());
        assertEquals(List.of("1", "2"), episodes.get(0).members().stream().map(EpisodePartitioner.AlertPoint::id).toList());
        assertEquals("3", episodes.get(1).anchor().id());
    }

    @Test
    void receiptTieUsesAlertIdAndAlertAtSealRemainsInEarlierEpisode() {
        var t = OffsetDateTime.parse("2026-09-23T00:00:00Z");
        var points = List.of(
                new EpisodePartitioner.AlertPoint("20", t, "FAILED"),
                new EpisodePartitioner.AlertPoint("10", t, "RECEIVED"),
                new EpisodePartitioner.AlertPoint("30", t.plusNanos(1000), "PROCESSED"));
        var episodes = new EpisodePartitioner().partition(points, List.of(t), Duration.ofMinutes(30));
        assertEquals(2, episodes.size());
        assertEquals(List.of("10", "20"), episodes.get(0).members().stream()
                .map(EpisodePartitioner.AlertPoint::id).toList());
        assertEquals("30", episodes.get(1).anchor().id());
    }

    private AlertV1Canonicalizer.CanonicalAlert alert(String environment, String resourceId,
            String severity, String title) {
        String json = """
                {"externalAlertId":"e-1","eventType":"FIRING","occurredAt":"2026-09-23T00:00:00Z",
                "ruleId":"Cpu","title":"%s","severity":"%s","environment":"%s",
                "service":"Api","resource":{"type":"Host","id":"%s"}}
                """.formatted(title, severity, environment, resourceId);
        return canonicalizer.canonicalize(canonicalizer.parseStructure(json.getBytes(StandardCharsets.UTF_8)));
    }
}
