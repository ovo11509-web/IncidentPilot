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

package com.nageoffer.ai.ragent.incident.core.dao;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Select;

/** Caller must remain in one database transaction from lock through the business write. */
public interface AggregationClockMapper {

    default OffsetDateTime allocateReceivedAt(String aggregationKey) {
        lockKey(aggregationKey);
        var allocated = nextReceivedAt(aggregationKey);
        if (allocated == null) {
            throw new IllegalStateException("Aggregation clock exhausted");
        }
        return allocated;
    }

    default OffsetDateTime allocateSealedAt(String aggregationKey) {
        lockKey(aggregationKey);
        var allocated = nextSealedAt(aggregationKey);
        if (allocated == null) {
            throw new IllegalStateException("Aggregation clock exhausted");
        }
        return allocated;
    }

    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{aggregationKey}, 0))")
    Object lockKey(String aggregationKey);

    @Select("""
            WITH previous AS (
                SELECT (SELECT max(received_at) FROM t_incident_alert
                        WHERE aggregation_key = #{aggregationKey}) AS last_received,
                       (SELECT max(lifecycle_sealed_at) FROM t_incident
                        WHERE aggregation_key = #{aggregationKey}) AS last_sealed
            )
            SELECT greatest(clock_timestamp(),
                    coalesce(last_received + interval '1 microsecond', '-infinity'::timestamptz),
                    coalesce(last_sealed + interval '1 microsecond', '-infinity'::timestamptz))
            FROM previous WHERE coalesce(isfinite(last_received), true) AND coalesce(isfinite(last_sealed), true)
            """)
    OffsetDateTime nextReceivedAt(String aggregationKey);

    @Select("""
            WITH previous AS (
                SELECT (SELECT max(received_at) FROM t_incident_alert
                        WHERE aggregation_key = #{aggregationKey}) AS last_received,
                       (SELECT max(lifecycle_sealed_at) FROM t_incident
                        WHERE aggregation_key = #{aggregationKey}) AS last_sealed
            )
            SELECT greatest(clock_timestamp(),
                    coalesce(last_received, '-infinity'::timestamptz),
                    coalesce(last_sealed, '-infinity'::timestamptz))
            FROM previous WHERE coalesce(isfinite(last_received), true) AND coalesce(isfinite(last_sealed), true)
            """)
    OffsetDateTime nextSealedAt(String aggregationKey);
}
