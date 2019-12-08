package org.apache.servicecomb.pack.omega.transport.dubbo;/*
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

import java.util.UUID;

import org.apache.servicecomb.pack.omega.context.IdGenerator;
import org.apache.servicecomb.pack.omega.context.OmegaContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackDubboProviderFilterTest {

  private static final String globalTxId = UUID.randomUUID().toString();
  private static final String localTxId = UUID.randomUUID().toString();
  private final OmegaContext omegaContext = new OmegaContext(new IdGenerator<String>() {
    @Override
    public String nextId() {
      return "ignored";
    }
  });
  private final Invocation invocation = mock(Invocation.class);
  private final Invoker invoker = mock(Invoker.class);
  
  private final PackDubboProviderFilter filter = new PackDubboProviderFilter();

  @Before
  public void setUp() {
    omegaContext.clear();
    filter.setOmegaContext(omegaContext);
  }

  @Test
  public void setUpOmegaContextInTransactionRequest() {
    when(invocation.getAttachment(OmegaContext.GLOBAL_TX_ID_KEY)).thenReturn(globalTxId);
    when(invocation.getAttachment(OmegaContext.LOCAL_TX_ID_KEY)).thenReturn(localTxId);

    doAnswer(new Answer<Void>() {
      // Just verify the context setting
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        assertThat(omegaContext.globalTxId(), is(globalTxId));
        assertThat(omegaContext.localTxId(), is(localTxId));
        return null;
      }
    }).when(invoker).invoke(invocation);

    filter.invoke(invoker, invocation);

    assertThat(omegaContext.globalTxId(), is(nullValue()));
    assertThat(omegaContext.localTxId(), is(nullValue()));
  }

  @Test
  public void doNothingInNonTransactionRequest() {
    when(invocation.getAttachment(OmegaContext.GLOBAL_TX_ID_KEY)).thenReturn(null);
    when(invocation.getAttachment(OmegaContext.LOCAL_TX_ID_KEY)).thenReturn(null);

    filter.invoke(invoker, invocation);

    assertThat(omegaContext.globalTxId(), is(nullValue()));
    assertThat(omegaContext.localTxId(), is(nullValue()));
  }

}
