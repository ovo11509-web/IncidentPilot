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

package com.nageoffer.ai.ragent.incident.observability;

import com.nageoffer.ai.ragent.framework.observability.ObservationScope;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker.DispatchClaim;
import io.opentelemetry.api.trace.SpanKind;

/** Business identities are supplied only by Incident's already-authoritative records. */
public final class IncidentObservation {
    private IncidentObservation() {}
    public static ObservationScope start(String name) { return ObservationScope.start(name, SpanKind.INTERNAL); }
    public static void alert(AlertMapper.AlertRow row) {
        if (row == null) return;
        ObservationScope.identify("alertId", row.getId());
        ObservationScope.identify("correlationId", row.getCorrelationId());
        ObservationScope.identify("incidentId", row.getIncidentId());
        ObservationScope.identify("processingEpoch", row.getProcessingEpoch());
    }
    public static void action(ActionMapper.ActionRow row) {
        if (row == null) return;
        ObservationScope.identify("actionId", row.getId());
        ObservationScope.identify("incidentId", row.getIncidentId());
    }
    public static void dispatch(DispatchClaim row) {
        if (row == null) return;
        ObservationScope.identify("actionId", row.actionId());
        ObservationScope.identify("incidentId", row.incidentId());
        ObservationScope.identify("executionId", row.executionId());
        ObservationScope.identify("dispatchRequestId", row.requestId());
    }
}
