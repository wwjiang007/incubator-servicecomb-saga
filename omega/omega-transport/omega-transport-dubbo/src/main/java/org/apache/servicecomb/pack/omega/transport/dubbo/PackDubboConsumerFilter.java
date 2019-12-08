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

package org.apache.servicecomb.pack.omega.transport.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

import java.lang.invoke.MethodHandles;

import org.apache.servicecomb.pack.omega.context.OmegaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.apache.servicecomb.pack.omega.context.OmegaContext.GLOBAL_TX_ID_KEY;
import static org.apache.servicecomb.pack.omega.context.OmegaContext.LOCAL_TX_ID_KEY;

/**
 * add saga transaction id to dubbo invocation
 */
@Activate(group = {Constants.CONSUMER})
public class PackDubboConsumerFilter implements Filter {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  // As we use the spring to manage the omegaContext, the Autowired work out of box
  @Autowired(required=false)
  private OmegaContext omegaContext;

  public void setOmegaContext(OmegaContext omegaContext) {
    this.omegaContext = omegaContext;
  }

  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (omegaContext != null) {
      invocation.getAttachments().put(GLOBAL_TX_ID_KEY, omegaContext.globalTxId());
      invocation.getAttachments().put(LOCAL_TX_ID_KEY, omegaContext.localTxId());
    }
    if (omegaContext != null && omegaContext.globalTxId() != null) {
      LOG.debug("Added {} {} and {} {} to dubbo invocation", new Object[] {GLOBAL_TX_ID_KEY, omegaContext.globalTxId(),
          LOCAL_TX_ID_KEY, omegaContext.localTxId()});
    } else {
      LOG.debug("Cannot inject transaction ID, as the OmegaContext is null or cannot get the globalTxId.");
    }

    if (invoker != null) {
      return invoker.invoke(invocation);
    }
    return null;
  }
}
