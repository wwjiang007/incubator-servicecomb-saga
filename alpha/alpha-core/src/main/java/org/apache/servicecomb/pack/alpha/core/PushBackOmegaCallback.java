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

import static org.apache.servicecomb.pack.common.EventType.TxCompensateEvent;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushBackOmegaCallback implements OmegaCallback {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final BlockingQueue<Runnable> pendingCompensations;
  private final OmegaCallback underlying;

  public PushBackOmegaCallback(BlockingQueue<Runnable> pendingCompensations, OmegaCallback underlying) {
    this.pendingCompensations = pendingCompensations;
    this.underlying = underlying;
  }

  @Override
  public void compensate(TxEvent event) {
    if(event.type().equals(TxCompensateEvent.name())){
      // actor call compensate
      underlying.compensate(event);
    }else{
      try {
        underlying.compensate(event);
      } catch (Exception e) {
        logError(event, e);
        pendingCompensations.offer(() -> compensate(event));
      }
    }
  }

  private void logError(TxEvent event, Exception e) {
    LOG.error(
        "Failed to {} service [{}] instance [{}] with method [{}], global tx id [{}] and local tx id [{}]",
        event.retries() == 0 ? "compensate" : "retry",
        event.serviceName(),
        event.instanceId(),
        event.retries() == 0 ? event.compensationMethod() : event.retryMethod(),
        event.globalTxId(),
        event.localTxId(),
        e);
  }
}
