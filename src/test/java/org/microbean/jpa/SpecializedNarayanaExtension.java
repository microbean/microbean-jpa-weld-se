/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.jpa;

import java.lang.reflect.Type;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import javax.inject.Singleton;

import javax.transaction.TransactionManager;

public final class SpecializedNarayanaExtension implements Extension {

  public SpecializedNarayanaExtension() {
    super();
  }

  private final void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
    event.addBean()
      .types(TransactionManager.class)
      .scope(Singleton.class)
      .createWith(cc -> com.arjuna.ats.jta.TransactionManager.transactionManager());
  }
  
  private final void onTypeDiscovery(@Observes final ProcessAnnotatedType<? extends com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorBase> event) {
    if (event != null) {
      final Annotated annotated = event.getAnnotatedType();
      if (annotated != null) {
        final Type baseType = annotated.getBaseType();
        assert baseType != null;
        final String name = baseType.getTypeName();
        assert name != null;
        if (name.startsWith("com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptor")) {
          event.veto();
        }
      }
    }
  }
  
}
