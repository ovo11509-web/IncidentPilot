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
import com.nageoffer.ai.ragent.framework.convention.Result;
import java.util.UUID;
public final class DiagnosisResponses {
    private DiagnosisResponses(){}
    public static <T> Result<T> success(T data){return new Result<T>().setCode("SUCCESS").setMessage("OK").setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId()).setData(data);}
}
