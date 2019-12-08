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

package org.apache.servicecomb.pack.omega.transaction;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.servicecomb.pack.common.EventType;

public class TxAbortedEvent extends TxEvent {

  private static final int PAYLOADS_MAX_LENGTH = 10240;

  public TxAbortedEvent(String globalTxId, String localTxId, String parentTxId, String compensationMethod, Throwable throwable) {
    super(EventType.TxAbortedEvent, globalTxId, localTxId, parentTxId, compensationMethod, 0, "", 0,
        stackTrace(throwable));
  }

  private static String stackTrace(Throwable e) {
    StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    String stackTrace = writer.toString();
    if (stackTrace.length() > PAYLOADS_MAX_LENGTH) {
      stackTrace = stackTrace.substring(0, PAYLOADS_MAX_LENGTH);
    }
    return stackTrace;
  }
}
