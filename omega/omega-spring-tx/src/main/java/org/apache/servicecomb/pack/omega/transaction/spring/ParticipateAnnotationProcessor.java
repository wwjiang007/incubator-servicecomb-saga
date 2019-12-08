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

package org.apache.servicecomb.pack.omega.transaction.spring;

import org.apache.servicecomb.pack.omega.context.CallbackContext;
import org.apache.servicecomb.pack.omega.context.OmegaContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

class ParticipateAnnotationProcessor implements BeanPostProcessor {

  private final OmegaContext omegaContext;

  private final CallbackContext callbackContext;

  ParticipateAnnotationProcessor(OmegaContext omegaContext, CallbackContext callbackContext) {
    this.omegaContext = omegaContext;
    this.callbackContext = callbackContext;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    checkMethod(bean);
    checkFields(bean);
    return bean;
  }

  private void checkMethod(Object bean) {
    ReflectionUtils.doWithMethods(
        bean.getClass(),
        new ParticipateMethodCheckingCallback(bean, callbackContext));
  }

  private void checkFields(Object bean) {
    ReflectionUtils.doWithFields(bean.getClass(), new ExecutorFieldCallback(bean, omegaContext));
  }
}
