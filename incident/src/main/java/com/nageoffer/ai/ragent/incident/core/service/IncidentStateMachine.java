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

import java.text.Normalizer;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Complete Spec A transition table; authorization is rechecked by the caller at the HTTP boundary. */
@Component
public final class IncidentStateMachine {
    private static final Set<String> ALLOWED = Set.of(
            "OPEN>ACKNOWLEDGED", "OPEN>RESOLVED", "ACKNOWLEDGED>MITIGATING",
            "ACKNOWLEDGED>RESOLVED", "MITIGATING>ACKNOWLEDGED", "MITIGATING>RESOLVED",
            "RESOLVED>ACKNOWLEDGED", "RESOLVED>CLOSED");
    private static final Set<String> REASON_REQUIRED = Set.of(
            "OPEN>RESOLVED", "ACKNOWLEDGED>RESOLVED", "MITIGATING>ACKNOWLEDGED",
            "MITIGATING>RESOLVED", "RESOLVED>ACKNOWLEDGED");

    public void requireAllowed(String from, String to, String reason, String actorType, String actorId) {
        if (!"USER".equals(actorType) || actorId == null || actorId.isBlank())
            throw new InvalidTransitionException(403);
        String edge = from + ">" + to;
        if (!ALLOWED.contains(edge)) throw new InvalidTransitionException(409);
        if (REASON_REQUIRED.contains(edge) && (reason == null || reason.strip().isEmpty()))
            throw new InvalidTransitionException(422);
        if (reason != null) {
            String clean = Normalizer.normalize(reason, Normalizer.Form.NFC).strip();
            if (clean.codePointCount(0, clean.length()) > 512 || clean.codePoints().anyMatch(Character::isISOControl))
                throw new InvalidTransitionException(422);
        }
    }

    public static final class InvalidTransitionException extends RuntimeException {
        private final int status;
        public InvalidTransitionException(int status) { this.status = status; }
        public int status() { return status; }
    }
}
