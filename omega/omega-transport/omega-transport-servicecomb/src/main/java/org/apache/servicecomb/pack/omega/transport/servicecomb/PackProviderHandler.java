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

package org.apache.servicecomb.pack.omega.transport.servicecomb;

import static org.apache.servicecomb.pack.omega.context.OmegaContext.GLOBAL_TX_ID_KEY;
import static org.apache.servicecomb.pack.omega.context.OmegaContext.LOCAL_TX_ID_KEY;

import java.lang.invoke.MethodHandles;

import org.apache.servicecomb.core.Handler;
import org.apache.servicecomb.core.Invocation;
import org.apache.servicecomb.foundation.common.utils.BeanUtils;
import org.apache.servicecomb.pack.omega.context.OmegaContext;
import org.apache.servicecomb.swagger.invocation.AsyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This provider is setup the OmegaContext before the application invocation.
 * Please make sure this handler is last one to use on the ServiceComb java-chassis application
 */
public class PackProviderHandler implements Handler {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OmegaContext omegaContext;

  public PackProviderHandler() {
    OmegaContext context = null;
    try {
      context = BeanUtils.getBean("omegaContext");
    } catch (NullPointerException npe) {
      LOG.warn("SagaProviderHandler cannot work rightly, please make sure omegaContext is in the spring application context.\"");
    }
    this.omegaContext = context;
  }

  public PackProviderHandler(OmegaContext omegaContext) {
    this.omegaContext = omegaContext;
  }

  @Override
  public void handle(Invocation invocation, AsyncResponse asyncResponse) throws Exception {
    if (omegaContext != null) {
      String globalTxId = invocation.getContext().get(GLOBAL_TX_ID_KEY);
      if (globalTxId == null) {
        LOG.debug("Cannot inject transaction ID, no such header: {}", GLOBAL_TX_ID_KEY);
      } else {
        omegaContext.setGlobalTxId(globalTxId);
        omegaContext.setLocalTxId(invocation.getContext().get(LOCAL_TX_ID_KEY));
      }
    } else {
      LOG.debug("Cannot inject transaction ID, as the OmegaContext is null.");
    }
    try {
      invocation.next(asyncResponse);
    } finally {
      // Clean up the OmegaContext
      if(omegaContext != null) {
        omegaContext.clear();
      }
    }
  }
}
