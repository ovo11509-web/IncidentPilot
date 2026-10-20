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

package com.nageoffer.ai.ragent.incident.action.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ActionPropertiesTest {
    @Test
    void defaultsAreSafeAndValid() {
        var properties = new ActionProperties();
        assertFalse(properties.getWorker().isEnabled());
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void rejectsInvalidBoundaries() {
        var properties = new ActionProperties();
        properties.setTicketTtl(Duration.ofMinutes(6));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setTicketTtl(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setTicketTtl(Duration.ofMinutes(2));
        properties.setPreConsumptionRetries(4);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
