/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.pack.alpha.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.servicecomb.pack.common.EventType.SagaEndedEvent;
import static org.apache.servicecomb.pack.common.EventType.TxAbortedEvent;
import static org.apache.servicecomb.pack.common.EventType.TxEndedEvent;
import static org.apache.servicecomb.pack.common.EventType.TxStartedEvent;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventScanner implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final byte[] EMPTY_PAYLOAD = new byte[0];

  private final ScheduledExecutorService scheduler;

  private final TxEventRepository eventRepository;

  private final CommandRepository commandRepository;

  private final TxTimeoutRepository timeoutRepository;

  private final OmegaCallback omegaCallback;

  private final int eventPollingInterval;

  private long nextEndedEventId;

  private long nextCompensatedEventId;

  private NodeStatus nodeStatus;

  public EventScanner(ScheduledExecutorService scheduler,
      TxEventRepository eventRepository,
      CommandRepository commandRepository,
      TxTimeoutRepository timeoutRepository,
      OmegaCallback omegaCallback,
      int eventPollingInterval,NodeStatus nodeStatus) {
    this.scheduler = scheduler;
    this.eventRepository = eventRepository;
    this.commandRepository = commandRepository;
    this.timeoutRepository = timeoutRepository;
    this.omegaCallback = omegaCallback;
    this.eventPollingInterval = eventPollingInterval;
    this.nodeStatus = nodeStatus;
  }

  @Override
  public void run() {
    try {
      // Need to catch the exception to keep the event scanner running.
      pollEvents();
    } catch (Exception ex) {
      LOG.warn("Got the exception {} when pollEvents.", ex.getMessage(), ex);
    }
  }

  private void pollEvents() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          // only pull the events when working in the master mode
          if(nodeStatus.isMaster()){
            updateTimeoutStatus();
            findTimeoutEvents();
            abortTimeoutEvents();
            saveUncompensatedEventsToCommands();
            compensate();
            updateCompensatedCommands();
            deleteDuplicateSagaEndedEvents();
            updateTransactionStatus();
          }
        },
        0,
        eventPollingInterval,
        MILLISECONDS);
  }

  private void findTimeoutEvents() {
    eventRepository.findTimeoutEvents()
        .forEach(event -> {
          LOG.info("Found timeout event {}", event);
          timeoutRepository.save(txTimeoutOf(event));
        });
  }

  private void updateTimeoutStatus() {
    timeoutRepository.markTimeoutAsDone();
  }

  private void saveUncompensatedEventsToCommands() {
    eventRepository.findFirstUncompensatedEventByIdGreaterThan(nextEndedEventId, TxEndedEvent.name())
        .forEach(event -> {
          LOG.info("Found uncompensated event {}", event);
          nextEndedEventId = event.id();
          commandRepository.saveCompensationCommands(event.globalTxId());
        });
  }

  private void updateCompensatedCommands() {
    eventRepository.findFirstCompensatedEventByIdGreaterThan(nextCompensatedEventId)
        .ifPresent(event -> {
          LOG.info("Found compensated event {}", event);
          nextCompensatedEventId = event.id();
          updateCompensationStatus(event);
        });
  }

  private void deleteDuplicateSagaEndedEvents() {
    try {
      eventRepository.deleteDuplicateEvents(SagaEndedEvent.name());
    } catch (Exception e) {
      LOG.warn("Failed to delete duplicate event", e);
    }
  }

  private void updateCompensationStatus(TxEvent event) {
    commandRepository.markCommandAsDone(event.globalTxId(), event.localTxId());
    LOG.info("Transaction with globalTxId {} and localTxId {} was compensated",
        event.globalTxId(),
        event.localTxId());

    markSagaEnded(event);
  }

  private void abortTimeoutEvents() {
    timeoutRepository.findFirstTimeout().forEach(timeout -> {
      LOG.info("Found timeout event {} to abort", timeout);

      eventRepository.save(toTxAbortedEvent(timeout));

      if (timeout.type().equals(TxStartedEvent.name())) {
        eventRepository.findTxStartedEvent(timeout.globalTxId(), timeout.localTxId())
            .ifPresent(omegaCallback::compensate);
      }
    });
  }

  private void updateTransactionStatus() {
    eventRepository.findFirstAbortedGlobalTransaction().ifPresent(this::markGlobalTxEndWithEvents);
  }

  private void markSagaEnded(TxEvent event) {
    if (commandRepository.findUncompletedCommands(event.globalTxId()).isEmpty()) {
      markGlobalTxEndWithEvent(event);
    }
  }

  private void markGlobalTxEndWithEvent(TxEvent event) {
    eventRepository.save(toSagaEndedEvent(event));
    LOG.info("Marked end of transaction with globalTxId {}", event.globalTxId());
  }

  private void markGlobalTxEndWithEvents(List<TxEvent> events) {
    events.forEach(this::markGlobalTxEndWithEvent);
  }

  private TxEvent toTxAbortedEvent(TxTimeout timeout) {
    return new TxEvent(
        timeout.serviceName(),
        timeout.instanceId(),
        timeout.globalTxId(),
        timeout.localTxId(),
        timeout.parentTxId(),
        TxAbortedEvent.name(),
        "",
        ("Transaction timeout").getBytes());
  }

  private TxEvent toSagaEndedEvent(TxEvent event) {
    return new TxEvent(
        event.serviceName(),
        event.instanceId(),
        event.globalTxId(),
        event.globalTxId(),
        null,
        SagaEndedEvent.name(),
        "",
        EMPTY_PAYLOAD);
  }
  
  private void compensate() {
    commandRepository.findFirstCommandToCompensate()
        .forEach(command -> {
          LOG.info("Compensating transaction with globalTxId {} and localTxId {}",
              command.globalTxId(),
              command.localTxId());

          omegaCallback.compensate(txStartedEventOf(command));
        });
  }

  private TxEvent txStartedEventOf(Command command) {
    return new TxEvent(
        command.serviceName(),
        command.instanceId(),
        command.globalTxId(),
        command.localTxId(),
        command.parentTxId(),
        TxStartedEvent.name(),
        command.compensationMethod(),
        command.payloads());
  }

  private TxTimeout txTimeoutOf(TxEvent event) {
    return new TxTimeout(
        event.id(),
        event.serviceName(),
        event.instanceId(),
        event.globalTxId(),
        event.localTxId(),
        event.parentTxId(),
        event.type(),
        event.expiryTime(),
        TaskStatus.NEW.name());
  }
}
