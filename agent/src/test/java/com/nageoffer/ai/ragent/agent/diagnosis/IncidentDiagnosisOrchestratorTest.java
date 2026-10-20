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

package com.nageoffer.ai.ragent.agent.diagnosis;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.Begin;
import com.nageoffer.ai.ragent.rag.runbook.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
class IncidentDiagnosisOrchestratorTest {
    @Test void localGenerationRunsAfterBeginAndOnlyOnceForSameRequest() {
        var records=mock(DiagnosisRecordService.class);var queries=mock(DiagnosisQueryService.class);var corpus=new RunbookCorpus();
        var snapshot=DiagnosisTestSnapshots.health().put("capturedAt","2026-10-03T12:00:00Z");var generator=spy(new ExtractiveDiagnosisGenerator(new RunbookRetriever(corpus)));
        doAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return call.callRealMethod();}).when(generator).generate(any(),any());
        when(records.begin(anyString(),anyString(),anyLong(),anyString(),anyString())).thenReturn(new Begin("101",snapshot,"owner",false,"RUNNING"),new Begin("101",snapshot,null,true,"COMPLETED"));
        when(queries.get("101")).thenReturn(Map.of("requestState","COMPLETED","outcome","SUPPORTED"));
        var service=new IncidentDiagnosisOrchestrator(records,queries,corpus,generator,new DiagnosisEvidenceValidator(generator));
        assertEquals(201,service.diagnose("100","request",0).status());assertEquals(200,service.diagnose("100","request",0).status());
        verify(records,times(1)).complete(any(),any());verify(records,times(2)).begin(anyString(),anyString(),anyLong(),anyString(),anyString());
    }
    @Test void unavailableGenerationIsStoredAsFailureWithoutFallback() {
        var records=mock(DiagnosisRecordService.class);var queries=mock(DiagnosisQueryService.class);var corpus=new RunbookCorpus();var generator=mock(ExtractiveDiagnosisGenerator.class);
        when(records.begin(anyString(),anyString(),anyLong(),anyString(),anyString())).thenReturn(new Begin("101",DiagnosisTestSnapshots.health().put("capturedAt","2026-10-03T12:00:00Z"),"owner",false,"RUNNING"));
        when(generator.generate(any(),any())).thenThrow(new IllegalStateException("unavailable"));when(queries.get("101")).thenReturn(Map.of("requestState","COMPLETED","outcome","FAILED"));
        new IncidentDiagnosisOrchestrator(records,queries,corpus,generator,new DiagnosisEvidenceValidator(generator)).diagnose("100","request",0);
        verify(records).complete(any(),argThat(v->v.path("status").asText().equals("FAILED")));
    }
}
