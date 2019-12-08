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

package org.apache.servicecomb.pack.omega.connector.grpc.saga;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.lang.invoke.MethodHandles;
import org.apache.servicecomb.pack.contract.grpc.ServerMeta;
import org.apache.servicecomb.pack.omega.connector.grpc.core.LoadBalanceContext;
import org.apache.servicecomb.pack.omega.context.ServiceConfig;
import org.apache.servicecomb.pack.omega.transaction.AlphaResponse;
import org.apache.servicecomb.pack.omega.transaction.MessageDeserializer;
import org.apache.servicecomb.pack.omega.transaction.MessageHandler;
import org.apache.servicecomb.pack.omega.transaction.MessageSerializer;
import org.apache.servicecomb.pack.omega.transaction.SagaMessageSender;
import org.apache.servicecomb.pack.omega.transaction.TxEvent;
import org.apache.servicecomb.pack.contract.grpc.GrpcAck;
import org.apache.servicecomb.pack.contract.grpc.GrpcServiceConfig;
import org.apache.servicecomb.pack.contract.grpc.GrpcTxEvent;
import org.apache.servicecomb.pack.contract.grpc.GrpcTxEvent.Builder;
import org.apache.servicecomb.pack.contract.grpc.TxEventServiceGrpc;
import org.apache.servicecomb.pack.contract.grpc.TxEventServiceGrpc.TxEventServiceBlockingStub;
import org.apache.servicecomb.pack.contract.grpc.TxEventServiceGrpc.TxEventServiceStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcSagaClientMessageSender implements SagaMessageSender {

  private final String target;

  private final TxEventServiceStub asyncEventService;

  private final MessageSerializer serializer;

  private final TxEventServiceBlockingStub blockingEventService;

  private final GrpcCompensateStreamObserver compensateStreamObserver;

  private final GrpcServiceConfig serviceConfig;

  public GrpcSagaClientMessageSender(
      String address,
      ManagedChannel channel,
      MessageSerializer serializer,
      MessageDeserializer deserializer,
      ServiceConfig serviceConfig,
      MessageHandler handler,
      LoadBalanceContext loadContext) {
    this.target = address;
    this.asyncEventService = TxEventServiceGrpc.newStub(channel);
    this.blockingEventService = TxEventServiceGrpc.newBlockingStub(channel);
    this.serializer = serializer;
    this.compensateStreamObserver =
        new GrpcCompensateStreamObserver(loadContext, this, handler, deserializer);
    this.serviceConfig = serviceConfig(serviceConfig.serviceName(), serviceConfig.instanceId());
  }

  @Override
  public void onConnected() {
    asyncEventService.onConnected(compensateStreamObserver).onNext(serviceConfig);
  }

  @Override
  public void onDisconnected() {
    blockingEventService.onDisconnected(serviceConfig);
  }

  @Override
  public ServerMeta onGetServerMeta() {
    return blockingEventService.onGetServerMeta(serviceConfig);
  }

  @Override
  public void close() {
    // just do nothing here
  }

  @Override
  public String target() {
    return target;
  }

  @Override
  public AlphaResponse send(TxEvent event) {
    GrpcAck grpcAck = blockingEventService.onTxEvent(convertEvent(event));
    return new AlphaResponse(grpcAck.getAborted());
  }

  private GrpcTxEvent convertEvent(TxEvent event) {
    ByteString payloads = ByteString.copyFrom(serializer.serialize(event.payloads()));

    Builder builder = GrpcTxEvent.newBuilder()
        .setServiceName(serviceConfig.getServiceName())
        .setInstanceId(serviceConfig.getInstanceId())
        .setTimestamp(event.timestamp())
        .setGlobalTxId(event.globalTxId())
        .setLocalTxId(event.localTxId())
        .setParentTxId(event.parentTxId() == null ? "" : event.parentTxId())
        .setType(event.type().name())
        .setTimeout(event.timeout())
        .setCompensationMethod(event.compensationMethod())
        .setRetryMethod(event.retryMethod() == null ? "" : event.retryMethod())
        .setRetries(event.retries())
        .setPayloads(payloads);

    return builder.build();
  }

  private GrpcServiceConfig serviceConfig(String serviceName, String instanceId) {
    return GrpcServiceConfig.newBuilder()
        .setServiceName(serviceName)
        .setInstanceId(instanceId)
        .build();
  }
}
