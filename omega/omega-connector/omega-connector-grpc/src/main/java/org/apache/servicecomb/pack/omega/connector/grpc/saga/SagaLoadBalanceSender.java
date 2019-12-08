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

package org.apache.servicecomb.pack.omega.connector.grpc.saga;

import com.google.common.base.Optional;

import org.apache.servicecomb.pack.omega.connector.grpc.core.LoadBalanceSenderAdapter;
import org.apache.servicecomb.pack.omega.connector.grpc.core.MessageSenderPicker;
import org.apache.servicecomb.pack.omega.connector.grpc.core.SenderExecutor;
import org.apache.servicecomb.pack.omega.connector.grpc.core.LoadBalanceContext;
import org.apache.servicecomb.pack.omega.transaction.AlphaResponse;
import org.apache.servicecomb.pack.omega.transaction.OmegaException;
import org.apache.servicecomb.pack.omega.transaction.SagaMessageSender;
import org.apache.servicecomb.pack.omega.transaction.TxEvent;

public class SagaLoadBalanceSender extends LoadBalanceSenderAdapter implements SagaMessageSender {

  public SagaLoadBalanceSender(LoadBalanceContext loadContext,
      MessageSenderPicker senderPicker) {
    super(loadContext, senderPicker);
  }

  @Override
  public AlphaResponse send(TxEvent event) {
    do {
      final SagaMessageSender messageSender = pickMessageSender();
      Optional<AlphaResponse> response = doGrpcSend(messageSender, event, new SenderExecutor<TxEvent>() {
        @Override
        public AlphaResponse apply(TxEvent event) {
          return messageSender.send(event);
        }
      });
      if (response.isPresent()) return response.get();
    } while (!Thread.currentThread().isInterrupted());

    throw new OmegaException("Failed to send event " + event + " due to interruption");
  }
}
