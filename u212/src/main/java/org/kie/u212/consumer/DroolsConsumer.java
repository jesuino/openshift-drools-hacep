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
package org.kie.u212.consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.kie.u212.core.Config;
import org.kie.u212.election.Callback;
import org.kie.u212.election.State;
import org.kie.u212.infra.OffsetManager;
import org.kie.u212.infra.PartitionListener;
import org.kie.u212.infra.consumer.ConsumerHandler;
import org.kie.u212.infra.consumer.EventConsumer;
import org.kie.u212.infra.utils.ConsumerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DroolsConsumer<T> implements EventConsumer , Callback {

  private Logger logger = LoggerFactory.getLogger(DroolsConsumer.class);
  private Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
  private org.apache.kafka.clients.consumer.Consumer<String, T> consumer;
  private ConsumerHandler consumerHandle;
  private String id;
  private String groupId;
  private boolean autoCommit;
  private volatile boolean leader = false;
  private boolean started;
  private boolean subscribe, assign;

  public DroolsConsumer(String id) {
    this.id = id;
  }

  @Override
  public void start(ConsumerHandler consumerHandler) {
    started = true;
    this.consumerHandle = consumerHandler;
    consumer = new KafkaConsumer<>(Config.getDefaultConfig());
  }

  @Override
  public void stop() {
    consumer.close();
    started = false;
  }

  @Override
  public void updateStatus(State state) {
    if(state.equals(State.LEADER) && !leader){
      leader = true;
      changeTopic(Config.MASTER_TOPIC);
      if(logger.isDebugEnabled()) {
        logger.debug("I'm the new Leader");
      }
    }else if(state.equals(State.NOT_LEADER) && leader){
      leader = false;
      changeTopic(Config.USERS_INPUT_TOPIC);
    }
  }

  @Override
  public void subscribe(String groupId,
                        String topic,
                        boolean autoCommit) {
    subscribe = true;
    this.autoCommit = autoCommit;
    this.groupId = groupId;
    consumer.subscribe(Collections.singletonList(topic), new PartitionListener(consumer, offsets));
  }

  @Override
  public void poll(int size, long duration, boolean commitSync) {
    final Thread mainThread = Thread.currentThread();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      logger.info("Starting exit...\n");
      consumer.wakeup();
      try {
        mainThread.join();
      } catch (InterruptedException e) {
        logger.error(e.getMessage(), e);
      }
    }));

    if (consumer == null) {
      throw new IllegalStateException("Can't poll, consumer not subscribed or null!");
    }

    try {
      if (duration == -1) {
        while (true) {
          consume(size, commitSync);
        }
      } else {
        long startTime = System.currentTimeMillis();
        while (false || (System.currentTimeMillis() - startTime) < duration) {
          consume(size, commitSync);
        }
      }
    } catch (WakeupException e) {
    } finally {
      try {
        //print offsets
        //sync does retries, we want to use it in case of last commit or rebalancing
        consumer.commitSync();
        if (logger.isDebugEnabled()) {
          for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
            logger.debug("Consumer %s - partition %s - lastOffset %s\n",
                         this.id,
                         entry.getKey().partition(),
                         entry.getValue().offset());
          }
        }
        OffsetManager.store(offsets); //Store offsets
      } finally {
        consumer.close();
      }
    }
  }

  @Override
  public boolean assign(String topic,
                        List partitions,
                        boolean autoCommit) {
    boolean isAssigned = false;
    List<PartitionInfo> partitionsInfo = consumer.partitionsFor(topic);
    Collection<TopicPartition> partitionCollection = new ArrayList<>();

    if (partitionsInfo != null) {
      for (PartitionInfo partition : partitionsInfo) {
        if (partitions == null || partitions.contains(partition.partition())) {
          partitionCollection.add(new TopicPartition(partition.topic(), partition.partition()));
        }
      }

      if (!partitionCollection.isEmpty()) {
        consumer.assign(partitionCollection);
        isAssigned = true;
      }
    }
    this.autoCommit = autoCommit;
    assign = isAssigned;
    return isAssigned;
  }


  @Override
  public void changeTopic(String newTopic) {
    consumer.unsubscribe();
    //consumer = new KafkaConsumer<>(Config.getDefaultConfig());
    consumer.subscribe(Collections.singletonList(newTopic), new PartitionListener(consumer, offsets));
    poll(Config.DEFAULT_POLL_SIZE, Config.LOOP_DURATION, true);
  }

  private void consume(int size, boolean commitSync) {
    ConsumerRecords<String, T> records = consumer.poll(size);
    for (ConsumerRecord<String, T> record : records) {
      //store next offset to commit
      offsets.put(new TopicPartition(record.topic(), record.partition()),
                  new OffsetAndMetadata(record.offset() + 1, "null"));
      consumerHandle.process(record);
      if (logger.isInfoEnabled()) {
        ConsumerUtils.prettyPrinter(id, groupId, record);
      }
    }

    if (!autoCommit) {
      if (!commitSync) {
        try {
          //async doesn't do a retry
          consumer.commitAsync((map, e) -> {
            if (e != null) {
              logger.error(e.getMessage(), e);
            }
          });
        } catch (CommitFailedException e) {
          logger.error(e.getMessage(), e);
        }
      } else {
        consumer.commitSync();
      }
    }
  }
}