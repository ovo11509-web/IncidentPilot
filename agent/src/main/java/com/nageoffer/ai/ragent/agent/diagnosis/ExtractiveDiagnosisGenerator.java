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

import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.rag.runbook.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reviewed, deterministic extraction. Source reports never establish a root cause. */
@Component public final class ExtractiveDiagnosisGenerator {
    private final RunbookRetriever retriever;
    public ExtractiveDiagnosisGenerator(RunbookRetriever retriever){this.retriever=retriever;}
    public ObjectNode generate(JsonNode snapshot,OffsetDateTime now) {
        var out=failureResult("INSUFFICIENT_EVIDENCE","NO_SUPPORTED_SYMPTOM");
        out.put("generatorVersion","extractive-v1");out.put("retrieverVersion","lexical-v1");
        out.put("ruleVersion","reviewed-rules-v1").put("promptVersion","NONE").put("retrievalConfigVersion","lexical-v1-top20-expanded");
        out.put("corpusVersion",retriever.corpus().version());out.put("corpusDigest",retriever.corpus().digest());
        out.put("snapshotDigest",snapshot.path("snapshotDigest").asText());
        out.putArray("missingFacts").add("尚无指标、日志或只读观测结果。");
        out.putArray("limitations").add("告警来源报告不能确认根因；需要人工只读检查。");
        if(snapshot.path("truncated").asBoolean() || snapshot.path("facts").isEmpty())return out;
        String query=query(snapshot);
        out.put("query",query);
        if(injected(query)) {out.put("reasonCode","UNTRUSTED_SOURCE_INSTRUCTION");return out;}
        if(query.toLowerCase(Locale.ROOT).matches("(?s).*(已恢复|已修复|健康正常|未发生|recovered|resolved|healthy).*")) {
            out.put("reasonCode","CONFLICTING_SOURCE_REPORT");return out;
        }
        var s=snapshot.path("scope");var scope=new RunbookEvidence.Scope(s.path("service").asText(),s.path("environment").asText(),s.path("resourceType").asText());
        java.util.List<RunbookEvidence> retrieved;
        try (var stage=com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.runbook.retrieve")) {
            retrieved=retriever.retrieve(query,scope,now); stage.outcome(retrieved.isEmpty()?"EMPTY":"RETRIEVED");
        }
        var docs=new LinkedHashMap<String,RunbookCorpus.Document>();
        for(var e:retrieved)if("symptoms".equals(e.sectionId())) {
            var d=retriever.corpus().documents().stream().filter(v->v.metadata().path("runbookId").asText().equals(e.runbookId())).findFirst().orElseThrow();
            // Require a published symptom keyword in the reported facts, rather than generic overlap.
            boolean matches=false;for(var term:d.metadata().path("symptomTerms"))
                if(query.toLowerCase(Locale.ROOT).contains(term.asText().toLowerCase(Locale.ROOT)))matches=true;
            if(matches)docs.put(e.runbookId(),d);
        }
        if(docs.size()!=1){out.put("reasonCode",docs.isEmpty()?"NO_SUPPORTED_SYMPTOM":"CONFLICTING_SYMPTOMS");return out;}
        var doc=docs.values().iterator().next();String rb=doc.metadata().path("runbookId").asText();
        if(doc.sections().stream().anyMatch(section->retrieved.stream().noneMatch(e->e.runbookId().equals(rb) && e.sectionId().equals(section.sectionId()))))return out;
        out.put("status","SUPPORTED");out.put("reasonCode","SOURCE_REPORT_ONLY");
        var evidence=out.withArray("evidence");
        // Retrieve linked rule sections from the matched document. Every quote comes from verified corpus bytes.
        var citations=new LinkedHashMap<String,String>();
        for(var section:doc.sections()) {
            var actual=retrieved.stream().filter(e->e.runbookId().equals(rb)&&e.sectionId().equals(section.sectionId())).findFirst().orElseThrow();
            String citation=actual.citationId();citations.put(section.sectionId(),citation);
            var item=evidence.addObject().put("citationId",citation).put("runbookId",rb).put("runbookVersion",actual.runbookVersion())
                .put("sectionId",section.sectionId()).put("lineStart",section.lineStart()).put("lineEnd",section.lineEnd())
                .put("quote",section.quote()).put("documentDigest",doc.metadata().path("documentDigest").asText()).put("chunkDigest",section.chunkDigest())
                .put("rank",actual.rank()).put("score",actual.score());
            item.set("scope",snapshot.path("scope").deepCopy());
            item.set("metadataSnapshot",actual.metadataSnapshot());
        }
        var stored=out.withArray("documentSnapshots").addObject().put("runbookId",rb).put("version",doc.metadata().path("version").asText())
            .put("normalizedContent",doc.content()).put("documentDigest",doc.metadata().path("documentDigest").asText());stored.set("metadataSnapshot",doc.metadata());
        var factIds=JSON.createArrayNode();for(var f:snapshot.path("facts"))factIds.add(f.path("factId").asText());
        var claim=out.withArray("claims").addObject().put("claimId","CL1").put("kind","CANDIDATE")
            .put("statement",doc.metadata().path("title").asText()+"：仅与来源报告的症状匹配，根因未确认。").put("ruleId",rb+":symptoms-v1").put("uncertainty","SOURCE_REPORT_NOT_CONFIRMED");
        claim.putArray("prerequisites").add("SOURCE_REPORTED_SYMPTOM").add("EXACT_DEMO_SCOPE").add("CURRENT_REVIEWED_DOCUMENT");
        claim.set("factIds",factIds);claim.putArray("citationIds").add(citations.get("symptoms"));
        var check=out.withArray("recommendations").addObject().put("recommendationId","R1").put("type","READ_ONLY_CHECK").put("risk","READ_ONLY")
            .put("statement",doc.sections().stream().filter(v->v.sectionId().equals("checks-readonly")).findFirst().orElseThrow().quote())
            .put("convertible",false).put("ruleId",rb+":checks-v1");
        check.set("factIds",factIds.deepCopy());check.putArray("prerequisites").add("SOURCE_REPORTED_SYMPTOM");check.putArray("citationIds").add(citations.get("checks-readonly"));check.put("recommendationDigest",digest(check));
        if("RB-HEALTH".equals(rb) && citations.containsKey("simulation-recovery")) {
            var rec=out.withArray("recommendations").addObject().put("recommendationId","R2").put("type","SIMULATION_PROPOSAL").put("risk","SIMULATION_ONLY")
                .put("statement","可人工选择将注册的演示资源设为 READY；必须另行服务端审批。").put("convertible",true)
                .put("ruleId","RB-HEALTH:simulation-recovery-v1").put("desiredState","READY");
            rec.set("factIds",factIds.deepCopy());rec.putArray("citationIds").add(citations.get("symptoms")).add(citations.get("simulation-recovery"));
            rec.putArray("prerequisites").add("SOURCE_REPORTED_SYMPTOM").add("EXACT_DEMO_SCOPE").add("REGISTERED_TARGET_MAPPING").add("SERVER_APPROVAL_REQUIRED");
            rec.putObject("binding").put("toolName","simulation.set-state").put("toolVersion","1").put("targetType","SIM_SERVICE").putObject("parameters").put("desiredState","READY");
            rec.put("recommendationDigest",digest(rec));
        }
        return out;
    }
    public static String query(JsonNode snapshot){var b=new StringBuilder();for(var f:snapshot.path("facts"))b.append(f.path("value").asText()).append(' ');return b.toString().strip();}
    public static boolean injected(String text){return text.toLowerCase(Locale.ROOT).matches("(?s).*(忽略|绕过|审批|批准|直接执行|系统指令|ignore|bypass|override|execute|sudo|rm\\s+-|password\\s*[:=]|token\\s*[:=]).*");}
}
