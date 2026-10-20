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

package com.nageoffer.ai.ragent;

import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import java.util.function.Consumer;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Prevents the isolated full-application test from reaching any broker. */
@Configuration
@Profile("isolated")
class IsolatedQueueConfiguration {
    @Bean
    MessageQueueProducer isolatedMessageQueueProducer() {
        return new MessageQueueProducer() {
            @Override
            public SendResult send(String topic, String keys, String bizDesc, Object body) {
                throw new AssertionError("unexpected isolated broker send");
            }

            @Override
            public void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                    Consumer<Object> localTransaction) {
                throw new AssertionError("unexpected isolated broker send");
            }
        };
    }
}
