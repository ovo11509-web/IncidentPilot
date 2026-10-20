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

package com.nageoffer.ai.ragent.incident.diagnosis.web;
import com.nageoffer.ai.ragent.incident.diagnosis.service.DiagnosisQueryService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/incident/v1") public final class DiagnosisQueryController {
    private final DiagnosisQueryService queries;
    public DiagnosisQueryController(DiagnosisQueryService queries){this.queries=queries;}
    @GetMapping("/diagnoses/{diagnosisId}") public Object get(@PathVariable String diagnosisId){return DiagnosisResponses.success(queries.get(diagnosisId));}
    @GetMapping("/incidents/{incidentId}/diagnoses") public Object list(@PathVariable String incidentId,
        @RequestParam(required=false) String afterId,@RequestParam(defaultValue="20") int limit){return DiagnosisResponses.success(queries.list(incidentId,afterId,limit));}
}
