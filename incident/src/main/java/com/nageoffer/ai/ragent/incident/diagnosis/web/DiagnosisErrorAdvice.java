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
import com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.Failure;
import com.nageoffer.ai.ragent.framework.convention.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.util.UUID;
@RestControllerAdvice(basePackages={"com.nageoffer.ai.ragent.incident.diagnosis","com.nageoffer.ai.ragent.agent.diagnosis"}) @Order(Ordered.HIGHEST_PRECEDENCE) public final class DiagnosisErrorAdvice {
    @ExceptionHandler(Failure.class) public Object failure(Failure e){return ResponseEntity.status(e.status()).body(new Result<>().setCode(e.code()).setMessage(e.code()).setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId()));}
    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class) public Object login(){return failure(new Failure(401,"AUTHENTICATION_REQUIRED"));}
    @ExceptionHandler(cn.dev33.satoken.exception.NotRoleException.class) public Object role(){return failure(new Failure(403,"ACCESS_DENIED"));}
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) public Object storage(){return failure(new Failure(503,"PERSISTENCE_UNAVAILABLE"));}
}
