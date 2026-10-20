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
import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import com.nageoffer.ai.ragent.rag.runbook.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
class DiagnosisOfflineEvaluationTest {
    @ParameterizedTest @ValueSource(strings={"cases-v1.json","cases-v2.json"})
    void actualExtractionAndFixedTamperingAreSeparate(String datasetFile) throws Exception {
        var startedAt=java.time.Instant.now();var dataset=JSON.readTree(Files.readString(Path.of("../rag/src/test/resources/fixtures/runbook/"+datasetFile)));
        var manifest=JSON.readTree(Files.readString(Path.of("../rag/src/test/resources/fixtures/runbook/corpus/manifest-v1.json")));
        var corpus=new RunbookCorpus(manifest,p->{try{return Files.readString(Path.of("../rag/src/test/resources/fixtures/runbook/corpus",RunbookCorpus.safePath(p)));}catch(Exception e){throw new IllegalStateException(e);}});
        int expectedCases=datasetFile.equals("cases-v2.json")?34:30;assertEquals(expectedCases,dataset.path("cases").size());
        assertEquals(dataset.path("corpusDigest").asText(),corpus.digest());var generator=new ExtractiveDiagnosisGenerator(new RunbookRetriever(corpus));var validator=new DiagnosisEvidenceValidator(generator);
        var report=JSON.createObjectNode().put("generatorVersion","extractive-v1").put("corpusDigest",corpus.digest()).put("datasetDigest",digest(dataset)).put("skipped",0).put("blocked",0).put("modelCalls",0).put("writeCalls",0);
        var cases=report.putArray("cases");int positives=0,supported=0,safe=0,rules=0,citations=0,negativeSafe=0;
        for(var c:dataset.path("cases")) {
            var snapshot=DiagnosisTestSnapshots.snapshot(c.path("queryFacts").get(0).asText(),c.path("scope"));var now=OffsetDateTime.parse(c.path("clock").asText());
            var output=generator.generate(snapshot,now);var row=cases.addObject().put("caseId",c.path("caseId").asText()).put("family",c.path("family").asText()).put("split",c.path("split").asText()).put("runType","ACTUAL_RULE_EXTRACTION").put("status",output.path("status").asText());row.set("output",output);row.set("expected",c);row.put("inputDigest",digest(snapshot)).put("suppliedFactCount",c.path("queryFacts").size()).put("consumedFactCount",snapshot.path("facts").size());
            assertEquals(c.path("expectedStatus").asText(),output.path("status").asText(),c.path("caseId").asText());assertTrue(validator.valid(snapshot,output,now));
            if(c.has("expectedRecommendationRules")){var actual=JSON.createArrayNode();for(var rec:output.path("recommendations"))actual.add(rec.path("ruleId").asText());assertEquals(c.path("expectedRecommendationRules"),actual,c.path("caseId").asText());}
            if(c.path("expectedStatus").asText().equals("SUPPORTED")){
                positives++;assertEquals(c.path("expectedClaimRules").get(0).asText(),output.path("claims").get(0).path("ruleId").asText());
                assertEquals("alert:11:title",output.path("claims").get(0).path("factIds").get(0).asText());
                assertEquals("READ_ONLY_CHECK",output.path("recommendations").get(0).path("type").asText());
                boolean convertible=false;for(var rec:output.path("recommendations"))convertible|=rec.path("convertible").asBoolean();assertEquals(c.path("expectedConvertible").asBoolean(),convertible);
                if(!output.path("claims").isEmpty())supported++;
            }
            else {for(var r:output.path("recommendations"))assertFalse(r.path("convertible").asBoolean());negativeSafe++;}safe++;
            rules+=output.path("claims").size()+output.path("recommendations").size();
            var ids=new java.util.HashSet<String>();for(var e:output.path("evidence")) {
                assertTrue(ids.add(e.path("citationId").asText()));assertEquals(e.path("chunkDigest").asText(),hash(e.path("quote").asText()));citations++;
                var document=output.path("documentSnapshots").get(0);String content=document.path("normalizedContent").asText();assertEquals(e.path("documentDigest").asText(),hash(content));
                var lines=content.split("\n",-1);String quote=String.join("\n",java.util.Arrays.copyOfRange(lines,e.path("lineStart").asInt()-1,e.path("lineEnd").asInt()))+"\n";assertEquals(e.path("quote").asText(),quote);
            }
            for(String field:new String[]{"claims","recommendations"})for(var item:output.path(field))for(var ref:item.path("citationIds"))assertTrue(ids.contains(ref.asText()));
        }
        var snapshot=DiagnosisTestSnapshots.health();var now=OffsetDateTime.parse("2026-10-03T12:00:00Z");var forged=generator.generate(snapshot,now);
        ((ObjectNode)forged.path("recommendations").get(1)).put("desiredState","DESTROY");
        assertEquals("INVALID_EVIDENCE",validator.validate(snapshot,forged,now).path("status").asText());
        cases.addObject().put("caseId","fixed-forged-write").put("runType","FIXED_TEST_RESPONSE").put("status","INVALID_EVIDENCE");
        report.put("actualCaseCount",safe).put("positiveDenominator",positives).put("positiveSupportedCount",supported).put("effectiveSuggestionCoverage",(double)supported/positives)
            .put("ruleSupportDenominator",rules).put("ruleSupportedCount",rules).put("ruleSupportRate",1.0).put("citationDenominator",citations).put("verifiedCitationCount",citations)
            .put("datasetVersion",dataset.path("datasetVersion").asText()).put("negativeDenominator",expectedCases-positives).put("safetyPassedCount",negativeSafe).put("executionCoverage",(double)safe/dataset.path("cases").size()).put("fixedResponseCount",1);
        assertTrue(rules>0);assertTrue(citations>0);assertEquals(expectedCases-positives,negativeSafe);
        var splits=report.putObject("splits");for(String split:new String[]{"development","holdout"}){int count=0,pos=0,neg=0;for(var row:cases)if(split.equals(row.path("split").asText())){count++;if("SUPPORTED".equals(row.path("status").asText()))pos++;else neg++;}splits.putObject(split).put("caseCount",count).put("positiveDenominator",pos).put("negativeDenominator",neg).put("safetyPassedCount",neg);}
        report.put("passed",safe+1).put("failed",0);DiagnosisEvaluationReport.write(report,dataset,startedAt,corpus.version());assertEquals(expectedCases,safe);assertEquals(10,positives);assertEquals(positives,supported);
    }
}
