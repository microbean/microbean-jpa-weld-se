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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import java.util.Set;

import javax.enterprise.context.Dependent;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;

import com.arjuna.ats.jta.cdi.TransactionContext;

public final class SpecializedNarayanaExtension implements Extension {

  public SpecializedNarayanaExtension() {
    super();
  }

  private final void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery event) throws ReflectiveOperationException {

    // Hack the TransactionContext class to not require JNDI.
    
    Field field = TransactionContext.class.getDeclaredField("transactionManager");
    assert field != null;
    assert Modifier.isStatic(field.getModifiers());
    assert Modifier.isPrivate(field.getModifiers());
    assert TransactionManager.class.equals(field.getType());
    field.setAccessible(true);
    field.set(null, com.arjuna.ats.jta.TransactionManager.transactionManager());

    field = TransactionContext.class.getDeclaredField("transactionSynchronizationRegistry");
    assert field != null;
    assert Modifier.isStatic(field.getModifiers());
    assert Modifier.isPrivate(field.getModifiers());
    assert TransactionSynchronizationRegistry.class.equals(field.getType());
    field.setAccessible(true);
    field.set(null, new TransactionSynchronizationRegistryImple());
  }
  
  private final void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
    // A note on the scope for this bean:
    // com.arjuna.ats.jta.TransactionManager#transactionManager()
    // returns a singleton.  But I suppose there is no guarantee in
    // the future that it will do so, and in any event the rest of the
    // Narayana infrastructure doesn't always cache the return value
    // of this method, so we want to preserve those semantics.
    event.addBean()
      .types(TransactionManager.class)
      .scope(Dependent.class)
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
          // Programmatically remove the interceptors installed by the
          // stock Narayana extension.  These will trigger a JNDI
          // lookup which will fail, since we are not guaranteed the
          // presence of a JNDI implementation in the microBean
          // environment.  We replace them with similar almost-clones
          // present in this package that bypass the JNDI machinery.
          // See also: https://github.com/jbosstm/narayana/pull/1344
          event.veto();
        }
      }
    }
  }
  
}
