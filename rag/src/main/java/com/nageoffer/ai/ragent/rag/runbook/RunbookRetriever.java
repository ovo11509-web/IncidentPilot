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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministic lexical baseline; deliberately has no model, embedding or MCP dependency. */
@Component public final class RunbookRetriever {
    private final RunbookCorpus corpus;
    public RunbookRetriever(RunbookCorpus corpus){this.corpus=corpus;}
    public List<RunbookEvidence> retrieve(String query,RunbookEvidence.Scope scope,OffsetDateTime now) {
        if(!corpus.available())throw new IllegalStateException("runbook corpus unavailable");
        if(query==null || query.length()>8192)throw new IllegalArgumentException("invalid runbook query");
        var terms=tokens(query);if(terms.isEmpty())return List.of();
        record Candidate(RunbookCorpus.Document doc,RunbookCorpus.Section section,double score){}
        var candidates=new ArrayList<Candidate>();
        for(var doc:corpus.documents())if(corpus.applicable(doc,scope,now))for(var section:doc.sections()) {
            var words=tokens(section.quote());long matched=terms.stream().filter(words::contains).count();
            if(matched>0)candidates.add(new Candidate(doc,section,(double)matched/terms.size()));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
            .thenComparing(c->c.doc().metadata().path("runbookId").asText())
            .thenComparing(c->c.doc().metadata().path("version").asText()).thenComparing(c->c.section().sectionId()));
        // Reviewed section expansion is part of retrieval, so generators cannot invent additional quotes.
        for(var doc:corpus.documents())if(corpus.applicable(doc,scope,now)) {
            boolean matched=false;for(var term:doc.metadata().path("symptomTerms"))
                if(query.toLowerCase(Locale.ROOT).contains(term.asText().toLowerCase(Locale.ROOT)))matched=true;
            if(matched)for(var section:doc.sections())if(candidates.stream().noneMatch(c->c.doc()==doc && c.section()==section))
                candidates.add(new Candidate(doc,section,0));
        }
        var results=new ArrayList<RunbookEvidence>();int bytes=0;
        for(var c:candidates.subList(0,Math.min(20,candidates.size()))) {
            bytes+=c.doc().content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(bytes>256*1024)throw new IllegalArgumentException("runbook evidence too large");
            var m=c.doc().metadata();var s=c.section();int rank=results.size()+1;
            results.add(new RunbookEvidence("C"+rank,m.path("runbookId").asText(),m.path("version").asText(),s.sectionId(),
                s.lineStart(),s.lineEnd(),s.quote(),m.path("documentDigest").asText(),s.chunkDigest(),rank,c.score(),scope,m,c.doc().content()));
        }
        return List.copyOf(results);
    }
    public static Set<String> tokens(String text) {
        var out=new TreeSet<String>();var matcher=Pattern.compile("[a-z0-9]+|[\\p{IsHan}]+").matcher(text.toLowerCase(Locale.ROOT));
        while(matcher.find()){String term=matcher.group();if(term.matches("[\\p{IsHan}]+")){
            if(term.length()==1)out.add(term);else for(int i=0;i<term.length()-1;i++)out.add(term.substring(i,i+2));
        }else out.add(term);}return out;
    }
    public RunbookCorpus corpus(){return corpus;}
}
