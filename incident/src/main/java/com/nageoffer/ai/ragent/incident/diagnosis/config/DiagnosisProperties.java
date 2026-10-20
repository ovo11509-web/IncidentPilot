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

package com.nageoffer.ai.ragent.incident.diagnosis.config;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import lombok.Data;

@Data @Configuration @ConfigurationProperties(prefix="incident.diagnosis")
public class DiagnosisProperties {
    private Map<String,String> simulationTargets=Map.of();
    @PostConstruct public void validate() {
        if(simulationTargets==null || simulationTargets.entrySet().stream().anyMatch(e->
            !e.getKey().equals("incident-demo:demo:sim_service") || !e.getValue().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
            throw new IllegalArgumentException("invalid diagnosis simulation mapping");
    }
}
