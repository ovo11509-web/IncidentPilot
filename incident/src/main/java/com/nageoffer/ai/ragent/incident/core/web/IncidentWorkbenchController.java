/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.nageoffer.ai.ragent.incident.core.web;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import com.nageoffer.ai.ragent.incident.core.service.IncidentWorkbenchQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/incident/v1/workbench")
public final class IncidentWorkbenchController {
    private final IncidentWorkbenchQueryService query;
    public IncidentWorkbenchController(IncidentWorkbenchQueryService query) {this.query=query;}
    @GetMapping
    public ResponseEntity<Result<Map<String,Object>>> workbench(HttpServletRequest request) {
        StpUtil.checkLogin();StpUtil.checkRole("admin");
        return ResponseEntity.ok(new Result<Map<String,Object>>().setCode(Result.SUCCESS_CODE).setMessage("OK")
            .setRequestId(AlertController.requestId(request)).setData(query.snapshot()));
    }
}
