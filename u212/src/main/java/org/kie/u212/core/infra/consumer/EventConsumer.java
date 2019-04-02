/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.u212.core.infra.consumer;

import java.util.List;
import java.util.Properties;

public interface EventConsumer<K, V> {

    void subscribe(String groupId,
                   String topic,
                   boolean autoCommit);

    void poll(int size,
              long duration,
              boolean commitSync);

    boolean assign(String topic,
                   List<Integer> partitions,
                   boolean autoCommit);

    void changeTopic(String newTopic);

    void createConsumer(ConsumerHandler consumerHandler,
               Properties properties);

    void stop();

    void waitStart(int pollSize,
                   long duration,
                   boolean commitSync);
}
