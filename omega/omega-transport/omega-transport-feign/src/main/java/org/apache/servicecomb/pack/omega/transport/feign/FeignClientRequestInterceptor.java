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

package org.apache.servicecomb.pack.omega.transport.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.apache.servicecomb.pack.omega.context.OmegaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.apache.servicecomb.pack.omega.context.OmegaContext.GLOBAL_TX_ID_KEY;
import static org.apache.servicecomb.pack.omega.context.OmegaContext.LOCAL_TX_ID_KEY;

/**
 * 增加Feign拦截器，实现spring cloud下feign调用传递全局事务和本地事务。
 * create by lionel on 2018/07/05
 */
public class FeignClientRequestInterceptor implements RequestInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final OmegaContext omegaContext;

    public FeignClientRequestInterceptor(OmegaContext omegaContext) {
        this.omegaContext = omegaContext;
    }

    @Override
    public void apply(RequestTemplate input) {
        if (omegaContext!= null && omegaContext.globalTxId() != null) {
            input.header(GLOBAL_TX_ID_KEY, omegaContext.globalTxId());
            input.header(LOCAL_TX_ID_KEY, omegaContext.localTxId());

            LOG.debug("Added {} {} and {} {} to request header",
                    GLOBAL_TX_ID_KEY,
                    omegaContext.globalTxId(),
                    LOCAL_TX_ID_KEY,
                    omegaContext.localTxId());
        } else {
            LOG.debug("Cannot inject transaction ID, as the OmegaContext is null or cannot get the globalTxId.");
        }
    }
}
