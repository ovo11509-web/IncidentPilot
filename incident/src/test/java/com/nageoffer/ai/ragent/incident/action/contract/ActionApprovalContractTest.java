/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.incident.action.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.service.ActionManualConclusionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReconciliationService;
import com.nageoffer.ai.ragent.incident.action.web.ActionController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActionApprovalContractTest {
    @Test
    void recoveryEndpointsEnforcePersonnelRoleAndStrictEvidenceBody() throws Exception {
        var proposals = mock(ActionProposalService.class);
        var decisions = mock(ActionDecisionService.class);
        var manual = mock(ActionManualConclusionService.class);
        var reconciliation = mock(ActionReconciliationService.class);
        var row = action();
        row.setState("UNKNOWN");
        row.setVersion(3);
        when(proposals.find("200")).thenReturn(row);
        var approvals = mock(ApprovalMapper.class);
        var approval = new ApprovalMapper.ApprovalRow();
        approval.setStatus("APPROVED");
        approval.setApprovalDueAt(java.time.OffsetDateTime.now().plusMinutes(15));
        when(approvals.findByActionId("200")).thenReturn(approval);
        var controller = new ActionController(proposals, decisions, approvals);
        controller.setRecoveryServices(manual, reconciliation);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        String conclusion = "{\"requestId\":\"req-1\",\"expectedVersion\":3,"
                + "\"outcome\":\"FAILED\",\"reason\":\"人工核查\",\"evidenceRef\":\"case:INC-1\"}";
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(post("/incident/v1/actions/200/manual-conclusion")
                            .contentType("application/json").content(conclusion))
                    .andExpect(status().isForbidden());
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            mvc.perform(post("/incident/v1/actions/200/manual-conclusion")
                            .contentType("application/json").content(conclusion))
                    .andExpect(status().isOk());
            mvc.perform(post("/incident/v1/actions/200/reconcile")
                            .contentType("application/json")
                            .content("{\"requestId\":\"req-2\",\"expectedVersion\":3}"))
                    .andExpect(status().isAccepted());
            mvc.perform(post("/incident/v1/actions/200/manual-conclusion")
                            .contentType("application/json").content(conclusion.replace(
                                    "\"evidenceRef\"", "\"unexpected\":1,\"evidenceRef\"")))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    private static final String PROPOSAL = """
            {"sourceRequestId":"request-1","toolName":"simulation.set-state","toolVersion":"1",
             "targetId":"demo","parameters":{"desiredState":"PAUSED"}}
            """;

    @Test
    void proposalUsesAuthenticatedUserAndDistinguishesNewRetryConflictAndBadInput() throws Exception {
        var proposals = mock(ActionProposalService.class);
        var decisions = mock(ActionDecisionService.class);
        var row = action();
        when(proposals.proposeUser(any())).thenReturn(new ActionProposalService.ProposalResult(row, false),
                new ActionProposalService.ProposalResult(row, true))
                .thenThrow(new ActionProposalService.SourceConflictException());
        when(proposals.find("200")).thenReturn(row);
        var mvc = mvc(proposals, decisions);
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.actionId").value("200"));
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionId").value("200"));
            mvc.perform(get("/incident/v1/actions/200"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("PENDING_APPROVAL"));
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL.replace("PAUSED", "READY")))
                    .andExpect(status().isConflict());
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL.replace("PAUSED", "READY").replace("\"parameters\"",
                                    "\"extra\":1,\"parameters\"")))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL.replace("\"sourceRequestId\"", "\"sourceType\":\"AGENT\",\"sourceRequestId\"")))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content("{bad"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void decisionRequiresCurrentSessionAndReportsStateConflict() throws Exception {
        var proposals = mock(ActionProposalService.class);
        var decisions = mock(ActionDecisionService.class);
        when(proposals.find("200")).thenReturn(action());
        when(decisions.decide(anyString(), anyLong(), anyString(), any(), anyString()))
                .thenReturn(action());
        var mvc = mvc(proposals, decisions);
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            mvc.perform(post("/incident/v1/actions/200/decision").contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/incident/v1/actions/200/decision").contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":-1}"))
                    .andExpect(status().isUnprocessableEntity());
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin)
                    .thenThrow(new NotLoginException("not logged in", "not_token", "login"));
            mvc.perform(get("/incident/v1/actions/200")).andExpect(status().isUnauthorized());
            mvc.perform(post("/incident/v1/actions/200/decision").contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isUnauthorized());
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(post("/incident/v1/incidents/100/actions").contentType("application/json")
                            .content(PROPOSAL)).andExpect(status().isForbidden());
            mvc.perform(post("/incident/v1/actions/200/decision").contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        }
    }

    private static ActionMapper.ActionRow action() {
        var row = new ActionMapper.ActionRow();
        row.setId("200");
        row.setIncidentId("100");
        row.setState("PENDING_APPROVAL");
        row.setToolName("simulation.set-state");
        row.setToolVersion("1");
        row.setTargetType("SIM_SERVICE");
        row.setTargetId("demo");
        row.setBindingDigest("a".repeat(64));
        return row;
    }

    private static MockMvc mvc(ActionProposalService proposals, ActionDecisionService decisions) {
        var approvals = mock(ApprovalMapper.class);
        var approval = new ApprovalMapper.ApprovalRow();
        approval.setStatus("PENDING");
        approval.setApprovalDueAt(java.time.OffsetDateTime.now().plusMinutes(15));
        when(approvals.findByActionId("200")).thenReturn(approval);
        return MockMvcBuilders.standaloneSetup(new ActionController(proposals, decisions, approvals))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
    }
}
