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
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
class RunbookOfflineEvaluationTest {
    @ParameterizedTest @ValueSource(strings={"cases-v1.json","cases-v2.json"})
    void actualLexicalRecallFiltersAndByteIntegrity(String datasetFile) throws Exception {
        var startedAt=java.time.Instant.now();var corpus=RunbookFixtureCorpus.load();var retriever=new RunbookRetriever(corpus);assertEquals(20,corpus.documents().size());
        var dataset=RunbookCorpus.JSON.readTree(getClass().getResourceAsStream("/fixtures/runbook/"+datasetFile));
        assertEquals(dataset.path("corpusDigest").asText(),corpus.digest());
        int expectedCases=datasetFile.equals("cases-v2.json")?34:30;assertEquals(expectedCases,dataset.path("cases").size());
        var inputKeys=new HashSet<String>();
        var report=RunbookCorpus.JSON.createObjectNode().put("runType","ACTUAL_LEXICAL_RETRIEVAL").put("corpusDigest",corpus.digest()).put("retrieverVersion","lexical-v1")
            .put("datasetDigest",RunbookCorpus.hash(RunbookCorpus.canonical(dataset))).put("skipped",0).put("blocked",0);
        var rows=report.putArray("cases");int hit=0,sectionHit=0,denominator=0,leak=0,integrity=0;var ids=new HashSet<String>();
        for(var c:dataset.path("cases")) {
            assertTrue(ids.add(c.path("caseId").asText()));
            var input=RunbookCorpus.JSON.createObjectNode().put("query",c.path("queryFacts").get(0).asText()).put("clock",c.path("clock").asText());input.set("scope",c.path("scope"));
            boolean fresh=inputKeys.add(RunbookCorpus.canonical(input));if(datasetFile.equals("cases-v2.json"))assertTrue(fresh,"duplicate consumed input: "+c.path("caseId"));
            var s=c.path("scope");var result=retriever.retrieve(c.path("queryFacts").get(0).asText(),
                new RunbookEvidence.Scope(s.path("service").asText(),s.path("environment").asText(),s.path("resourceType").asText()),OffsetDateTime.parse(c.path("clock").asText()));
            var row=rows.addObject().put("caseId",c.path("caseId").asText()).put("split",c.path("split").asText());row.set("retrieved",RunbookCorpus.JSON.valueToTree(result));row.put("family",c.path("family").asText()).put("inputDigest",RunbookCorpus.hash(RunbookCorpus.canonical(c)));row.set("expected",c);
            if(c.path("category").asText().startsWith("positive")) {
                denominator++;String gold=c.path("goldRunbookIds").get(0).asText();var top=result.stream().limit(3).toList();
                boolean h=top.stream().anyMatch(e->e.runbookId().equals(gold)),sh=top.stream().anyMatch(e->e.runbookId().equals(gold)&&e.sectionId().equals("symptoms"));
                if(h)hit++;if(sh)sectionHit++;row.put("hitAt3",h).put("sectionHitAt3",sh);
            }
            for(var e:result){
                var metadata=e.metadataSnapshot();var clock=OffsetDateTime.parse(c.path("clock").asText());
                if(!contains(metadata.path("services"),s.path("service").asText())||!contains(metadata.path("environments"),s.path("environment").asText())
                    ||!contains(metadata.path("resourceTypes"),s.path("resourceType").asText())||!"PUBLISHED".equals(metadata.path("status").asText())
                    ||!metadata.path("version").equals(metadata.path("currentVersion"))||clock.isBefore(OffsetDateTime.parse(metadata.path("reviewedAt").asText()))
                    ||!clock.isBefore(OffsetDateTime.parse(metadata.path("validUntil").asText())))leak++;
                assertEquals(e.documentDigest(),RunbookCorpus.hash(e.normalizedContent()));assertEquals(e.chunkDigest(),RunbookCorpus.hash(e.quote()));integrity++;}
        }
        report.put("datasetVersion",dataset.path("datasetVersion").asText()).put("distinctConsumedInputCount",inputKeys.size()).put("caseCount",ids.size()).put("positiveDenominator",denominator).put("hitCount",hit).put("sectionHitCount",sectionHit)
            .put("hitAt3",(double)hit/denominator).put("sectionHitAt3",(double)sectionHit/denominator).put("leakCount",leak).put("verifiedCitationCount",integrity);
        var splits=report.putObject("splits");for(String split:List.of("development","holdout")) {
            int splitCount=0,splitPositive=0,splitHit=0,splitSection=0;var negativeCategories=new HashSet<String>();for(var row:rows)if(split.equals(row.path("split").asText())) {
                splitCount++;if(!row.has("hitAt3"))negativeCategories.add(row.path("expected").path("category").asText());if(row.has("hitAt3")){splitPositive++;if(row.path("hitAt3").asBoolean())splitHit++;if(row.path("sectionHitAt3").asBoolean())splitSection++;}
            }
            splits.putObject(split).put("caseCount",splitCount).put("positiveDenominator",splitPositive).put("hitCount",splitHit).put("sectionHitCount",splitSection).put("negativeDenominator",splitCount-splitPositive);
            if(datasetFile.equals("cases-v2.json")&&split.equals("holdout"))assertEquals(Set.of("insufficient","wrong-scope","expired","injection"),negativeCategories);
            assertTrue(splitPositive>0);assertTrue((double)splitHit/splitPositive>=.9);assertTrue((double)splitSection/splitPositive>=.9);
        }
        var families=report.putObject("families");for(var row:rows){String family=row.path("family").asText();var f=families.withObject("/"+family);f.put("caseCount",f.path("caseCount").asInt()+1);if(row.has("hitAt3")){f.put("positiveDenominator",f.path("positiveDenominator").asInt()+1);f.put("hitCount",f.path("hitCount").asInt()+(row.path("hitAt3").asBoolean()?1:0));f.put("sectionHitCount",f.path("sectionHitCount").asInt()+(row.path("sectionHitAt3").asBoolean()?1:0));}}
        report.put("passed",ids.size()).put("failed",0).put("executionCoverage",1.0);RunbookEvaluationReport.write(report,dataset,startedAt,corpus.version());gate(ids.size(),denominator,hit,sectionHit,leak);
    }
    private static boolean contains(com.fasterxml.jackson.databind.JsonNode array,String value){for(var n:array)if(n.asText().equals(value))return true;return false;}
    static void gate(int count,int denominator,int hit,int section,int leak){assertTrue(count>=30);assertTrue(denominator>0);assertTrue((double)hit/denominator>=.9);assertTrue((double)section/denominator>=.9);assertEquals(0,leak);}
    @Test void gateRejectsMissingZeroDenominatorAndLowRecall(){assertThrows(AssertionError.class,()->gate(29,10,10,10,0));assertThrows(AssertionError.class,()->gate(30,0,0,0,0));assertThrows(AssertionError.class,()->gate(30,10,8,10,0));}
}
