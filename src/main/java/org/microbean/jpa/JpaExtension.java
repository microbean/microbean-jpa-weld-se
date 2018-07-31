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

import java.io.IOException;

import java.net.URL;

import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Dependent;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;

import javax.enterprise.util.TypeLiteral;

import javax.inject.Named;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import javax.transaction.TransactionScoped;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.microbean.jpa.annotation.JTA;

public class JpaExtension implements Extension {

  private static final Type providersType = new TypeLiteral<List<PersistenceProvider>>() {
      private static final long serialVersionUID = 1L;
    }.getType();
      
  
  public JpaExtension() {
    super();
  }

  private final void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) throws IOException, JAXBException {
    if (event != null && beanManager != null) {

      // Add all persistence units we can find, regardless of whether
      // they're referenced or not.

      final JAXBContext jaxbContext = JAXBContext.newInstance("org.microbean.jpa.jaxb");
      assert jaxbContext != null;
      final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      assert unmarshaller != null;

      final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
      assert tccl != null;
      final Enumeration<URL> urls = tccl.getResources("META-INF/persistence.xml");
      assert urls != null;
      final List<Persistence> persistences = new ArrayList<>();
      while (urls.hasMoreElements()) {
        final URL persistenceXmlUrl = urls.nextElement();
        assert persistenceXmlUrl != null;
        final URL rootUrl = new URL(persistenceXmlUrl, "../..");
        
        final org.microbean.jpa.jaxb.Persistence persistence = (org.microbean.jpa.jaxb.Persistence)unmarshaller.unmarshal(persistenceXmlUrl);
        assert persistence != null;

        final Collection<? extends PersistenceUnitInfo> persistenceUnitInfos =
          PersistenceUnitInfoBean.fromPersistence(persistence,
                                                  rootUrl,
                                                  null,
                                                  null);
        assert persistenceUnitInfos != null;
        for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
          assert persistenceUnitInfo != null;

          final String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
          if (persistenceUnitName != null) {

            final Named namedLiteral = NamedLiteral.of(persistenceUnitName);
            assert namedLiteral != null;
            
            event.<PersistenceUnitInfo>addBean()
              .types(PersistenceUnitInfo.class)
              .scope(ApplicationScoped.class)
              .addQualifiers(namedLiteral)
              .createWith(cc -> persistenceUnitInfo);

            // Add a bean that creates an EntityManagerFactory instance
            // for each persistence unit, taking into account
            // container-managed vs. application-managed and transactional
            // concerns.
            event.<EntityManagerFactory>addBean()
              .types(Collections.singleton(EntityManagerFactory.class))
              .scope(ApplicationScoped.class)
              .addQualifiers(namedLiteral)
              .createWith(cc -> {
                  
                  // Get the List of PersistenceProviders.
                  Bean<?> bean = beanManager.resolve(beanManager.getBeans(providersType));
                  assert bean != null;
                  @SuppressWarnings("unchecked")
                    final List<PersistenceProvider> providers = (List<PersistenceProvider>)beanManager.getReference(bean, providersType, beanManager.createCreationalContext(bean));
                  assert providers != null;
                  assert !providers.isEmpty();
                  
                  // See if there are any appropriately-named
                  // PersistenceUnitInfo beans.  We'd expect only one.
                  final Set<Bean<?>> beans = beanManager.getBeans(PersistenceUnitInfo.class, namedLiteral);
                  assert beans != null;
                  final PersistenceUnitInfo persistenceUnit;
                  if (!beans.isEmpty()) {
                    // If there any appropriately-named
                    // PersistenceUnitInfo beans, then resolve them and
                    // get the sole instance (or die trying).
                    bean = beanManager.resolve(beans);
                    assert bean != null;
                    persistenceUnit = (PersistenceUnitInfo)beanManager.getReference(bean, PersistenceUnitInfo.class, beanManager.createCreationalContext(bean));
                  } else {
                    // There were no appropriately-named
                    // PersistenceUnitInfo beans at all, so there's no way
                    // to get a PersistenceUnitInfo instance.
                    persistenceUnit = null;
                  }
                  
                  final EntityManagerFactory returnValue;
                  if (persistenceUnit == null) {
                    // If there wasn't a PersistenceUnitInfo, then we're
                    // just creating an application-managed
                    // EntityManagerFactory as if "by hand".
                    returnValue = createEntityManagerFactory(persistenceUnitName, providers);
                  } else {
                    // If there was a PersistenceUnitInfo, then we're
                    // creating a container-managed EntityManagerFactory
                    // using either RESOURCE_LOCAL or JTA transactions.
                    returnValue = createEntityManagerFactory(persistenceUnit, providers, beanManager);
                  }
                  
                  return returnValue;
                })          
              .destroyWith((emf, instance) -> {
                  if (emf.isOpen()) {
                    emf.close();
                  }
                });
            
            // Add a bean that creates an appropriately-named
            // EntityManager in transaction scope.
            event.<EntityManager>addBean()
              .types(Collections.singleton(EntityManager.class))
              .scope(TransactionScoped.class)
              .addQualifiers(namedLiteral, JTA.Literal.INSTANCE)
              .createWith(cc -> {
                  final Bean<?> bean = beanManager.resolve(beanManager.getBeans(EntityManagerFactory.class, namedLiteral));
                  assert bean != null;
                  final EntityManagerFactory emf = (EntityManagerFactory)beanManager.getReference(bean, EntityManagerFactory.class, beanManager.createCreationalContext(bean));
                  assert emf != null;
                  return emf.createEntityManager();
                })
              .destroyWith((em, instance) -> {
                  if (em.isOpen()) {
                    assert em.isJoinedToTransaction();
                    em.close();
                  }
                });
            
            // Add a bean that checks to see if there is already a
            // transaction-scoped EntityManager.  If there is, return it
            // in Dependent scope.  If there isn't, just create a new one.
            event.<EntityManager>addBean()
              .types(Collections.singleton(EntityManager.class))
              .scope(Dependent.class)
              .addQualifiers(namedLiteral)
              .createWith(cc -> {
                  final Bean<?> persistenceUnitInfoBean = beanManager.resolve(beanManager.getBeans(PersistenceUnitInfo.class, namedLiteral));
                  assert persistenceUnitInfoBean != null;
                  final PersistenceUnitInfo pu = (PersistenceUnitInfo)beanManager.getReference(persistenceUnitInfoBean, PersistenceUnitInfo.class, beanManager.createCreationalContext(persistenceUnitInfoBean));
                  assert pu != null;
                  final PersistenceUnitTransactionType transactionType = pu.getTransactionType();
                  final EntityManager em;
                  if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(transactionType)) {
                    final Bean<?> emfBean = beanManager.resolve(beanManager.getBeans(EntityManagerFactory.class, namedLiteral));
                    assert emfBean != null;
                    final EntityManagerFactory emf = (EntityManagerFactory)beanManager.getReference(emfBean, EntityManagerFactory.class, beanManager.createCreationalContext(emfBean));
                    assert emf != null;
                    em = emf.createEntityManager();
                  } else {
                    final Set<Bean<?>> beans = beanManager.getBeans(EntityManager.class, namedLiteral, JTA.Literal.INSTANCE);
                    assert beans != null : "null Set<Bean> for an EntityManager named " + namedLiteral.value();
                    assert !beans.isEmpty() : "No Beans present for an EntityManager named " + namedLiteral.value();
                    final Bean<?> emBean = beanManager.resolve(beans);
                    em = (EntityManager)beanManager.getReference(emBean, EntityManager.class, beanManager.createCreationalContext(emBean));
                  }
                  return em;
                })
              .destroyWith((em, instance) -> {
                  try {
                    if (em.isOpen() && !em.isJoinedToTransaction()) {
                      // We close a Dependent-scoped EntityManager only if
                      // we know that when we created it no JTA transaction
                      // was in effect.  Otherwise this EntityManager is a
                      // transaction-scoped EntityManager and we shouldn't
                      // destroy it; the transaction going out of scope
                      // should do that automatically.
                      em.close();
                    }
                  } catch (final ContextNotActiveException contextNotActiveException) {
                    // This is actually OK; it means that the JTA
                    // transaction already closed, and the entity
                    // manager was a JTA one, and was destroyed
                    // already.
                  }
                });
          }
        }
      }
      
    }
  }

  private static final EntityManagerFactory createEntityManagerFactory(final String persistenceUnitName, final Collection<? extends PersistenceProvider> providers) {
    Objects.requireNonNull(persistenceUnitName);
    Objects.requireNonNull(providers);
    if (providers.isEmpty()) {
      throw new IllegalArgumentException("providers.isEmpty()");
    }
    EntityManagerFactory returnValue = null;
    for (final PersistenceProvider provider : providers) {
      assert provider != null;
      returnValue = provider.createEntityManagerFactory(persistenceUnitName, Collections.emptyMap());
      if (returnValue != null) {
        break;
      }
    }
    return returnValue;
  }

  private static final EntityManagerFactory createEntityManagerFactory(final PersistenceUnitInfo persistenceUnitInfo, final Collection<? extends PersistenceProvider> providers, final BeanManager beanManager) {
    Objects.requireNonNull(persistenceUnitInfo);
    Objects.requireNonNull(providers);
    Objects.requireNonNull(beanManager);
    if (providers.isEmpty()) {
      throw new IllegalArgumentException("providers.isEmpty()");
    }
    EntityManagerFactory returnValue = null;
    final Map<String, Object> properties = new HashMap<>();
    properties.put("javax.persistence.bean.manager", beanManager);
    for (final PersistenceProvider provider : providers) {
      assert provider != null;
      returnValue = provider.createContainerEntityManagerFactory(persistenceUnitInfo, Collections.unmodifiableMap(properties));
      if (returnValue != null) {
        break;
      }
    }
    return returnValue;
  }

  private static final boolean isContainerManagedEntityManagerFactory(final EntityManagerFactory emf) {
    final boolean returnValue;
    if (emf == null) {
      returnValue = false;
    } else {
      final Map<?, ?> properties = emf.getProperties();
      if (properties == null || properties.isEmpty()) {
        returnValue = false;
      } else {
        returnValue = properties.get("javax.persistence.bean.manager") instanceof BeanManager;
      }
    }
    return returnValue;
  }
  
}
