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

package com.nageoffer.ai.ragent.rag.runbook;
import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RunbookRetrievalTest {
    @Test void scopeExpiryAndStableRankingAreEnforcedWithoutExternalCalls() {
        var retriever = new RunbookRetriever(new RunbookCorpus());
        var now = OffsetDateTime.parse("2026-10-03T00:00:00Z");
        var scope = new RunbookEvidence.Scope("incident-demo", "demo", "sim_service");
        var result = retriever.retrieve("OutOfMemoryError heap oom", scope, now);
        assertFalse(result.isEmpty()); assertEquals("RB-JAVA-OOM", result.get(0).runbookId());
        assertEquals(result, retriever.retrieve("OutOfMemoryError heap oom", scope, now));
        assertTrue(retriever.retrieve("oom", new RunbookEvidence.Scope("production", "prod", "host"), now).isEmpty());
        assertTrue(retriever.retrieve("oom", scope, now.plusYears(20)).isEmpty());
        assertTrue(retriever.retrieve("zzzzzz", scope, now).isEmpty());
    }
}
