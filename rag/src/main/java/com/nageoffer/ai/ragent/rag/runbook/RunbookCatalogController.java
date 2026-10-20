/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package com.nageoffer.ai.ragent.rag.runbook;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.framework.convention.Result;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Personnel-only immutable packaged corpus; never opens a user-supplied path. */
@RestController
@RequestMapping("/incident/v1/runbooks")
public final class RunbookCatalogController {
    private final RunbookCorpus corpus;
    public RunbookCatalogController(RunbookCorpus corpus) {this.corpus=corpus;}

    @GetMapping
    public ResponseEntity<Result<Map<String,Object>>> catalog() {
        StpUtil.checkLogin();StpUtil.checkRole("admin");
        if(!corpus.available())return failure(503,"RUNBOOK_CORPUS_UNAVAILABLE");
        var documents=corpus.documents();
        if(documents.size()>100 || documents.stream().anyMatch(d->d.content().length()>131072)
            || documents.stream().mapToLong(d->d.content().length()).sum()>1048576)
            return failure(503,"RUNBOOK_CORPUS_UNAVAILABLE");
        var items=new ArrayList<Map<String,Object>>();
        for(var doc:documents) {
            var metadata=doc.metadata();var out=new LinkedHashMap<String,Object>();
            for(String key:List.of("runbookId","version","currentVersion","title","owner","author","sourceType","status","reviewedAt","validUntil","documentDigest"))
                out.put(key,metadata.path(key).asText());
            for(String key:List.of("services","environments","resourceTypes")) {
                var values=new ArrayList<String>();metadata.path(key).forEach(n->values.add(n.asText()));out.put(key,List.copyOf(values));
            }
            out.put("content",doc.content());
            out.put("sections",doc.sections().stream().map(s->Map.of("sectionId",s.sectionId(),"lineStart",s.lineStart(),"lineEnd",s.lineEnd(),"quote",s.quote(),"chunkDigest",s.chunkDigest())).toList());
            items.add(out);
        }
        return ResponseEntity.ok(new Result<Map<String,Object>>().setCode(Result.SUCCESS_CODE).setMessage("OK")
            .setRequestId(UUID.randomUUID().toString()).setData(Map.of("observedAt",OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "corpusVersion",corpus.version(),"corpusDigest",corpus.digest(),"documents",items)));
    }
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Map<String,Object>>> unauthorized(NotLoginException ignored) {return failure(401,"UNAUTHORIZED");}
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Map<String,Object>>> forbidden(NotRoleException ignored) {return failure(403,"FORBIDDEN");}
    private static ResponseEntity<Result<Map<String,Object>>> failure(int status,String code) {
        return ResponseEntity.status(status).body(new Result<Map<String,Object>>().setCode(code).setMessage(code).setRequestId(UUID.randomUUID().toString()));
    }
}
