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

package com.nageoffer.ai.ragent.incident.core.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Partitions all persisted members by trusted receipt order, window gaps and immutable seal cuts. */
@Component
public final class EpisodePartitioner {
    public List<Episode> partition(List<AlertPoint> alerts, List<OffsetDateTime> seals, Duration window) {
        if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("window must be positive");
        var ordered = alerts.stream().sorted(Comparator.comparing(AlertPoint::receivedAt)
                .thenComparing(AlertPoint::id)).toList();
        var cuts = seals.stream().sorted().toList();
        List<Episode> result = new ArrayList<>();
        List<AlertPoint> current = new ArrayList<>();
        AlertPoint previous = null;
        for (AlertPoint point : ordered) {
            if (previous != null && (Duration.between(previous.receivedAt(), point.receivedAt()).compareTo(window) > 0
                    || hasSealBetween(previous.receivedAt(), point.receivedAt(), cuts))) {
                result.add(new Episode(current.get(0), List.copyOf(current)));
                current.clear();
            }
            current.add(point);
            previous = point;
        }
        if (!current.isEmpty()) result.add(new Episode(current.get(0), List.copyOf(current)));
        return List.copyOf(result);
    }

    private static boolean hasSealBetween(OffsetDateTime before, OffsetDateTime after,
            List<OffsetDateTime> seals) {
        for (OffsetDateTime seal : seals) {
            if (seal.isBefore(before)) continue;
            if (seal.isBefore(after)) return true;
            break;
        }
        return false;
    }

    public record AlertPoint(String id, OffsetDateTime receivedAt, String processingStatus) {}
    public record Episode(AlertPoint anchor, List<AlertPoint> members) {}
}
