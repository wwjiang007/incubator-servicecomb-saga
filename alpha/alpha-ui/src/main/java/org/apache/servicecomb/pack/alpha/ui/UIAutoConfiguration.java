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

package org.apache.servicecomb.pack.alpha.ui;

import org.apache.servicecomb.pack.alpha.ui.config.WebSocketConfig;
import org.apache.servicecomb.pack.alpha.ui.controller.IndexController;
import org.apache.servicecomb.pack.alpha.ui.controller.TransactionController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(value = {"alpha.feature.akka.enabled"})
@Import({WebSocketConfig.class, IndexController.class, TransactionController.class})
public class UIAutoConfiguration {

  @Bean
  public RestTemplate restTemplate(){
    return new RestTemplate();
  }
}
