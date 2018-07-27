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

import java.lang.annotation.Annotation;

import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Dependent;

import javax.enterprise.context.spi.Context;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.Extension;

import javax.enterprise.util.TypeLiteral;

import javax.inject.Named;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;

import javax.transaction.TransactionScoped;

import org.microbean.jpa.annotation.JTA;

public class JpaExtension implements Extension {

  private static final Type persistenceUnitInfoInstanceType = new TypeLiteral<Instance<PersistenceUnitInfo>>() {
      private static final long serialVersionUID = 1L;
    }.getType();

  private static final Type providersType = new TypeLiteral<List<PersistenceProvider>>() {
      private static final long serialVersionUID = 1L;
    }.getType();
      
  
  private final Set<String> persistenceUnitNames;
  
  public JpaExtension() {
    super();
    this.persistenceUnitNames = new HashSet<>();
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processPersistenceUnitInfoBean(@Observes final ProcessBean<? extends PersistenceUnitInfo> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getBean());
    }
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processPersistenceUnitInfoProducerMethod(@Observes final ProcessProducer<?, ? extends PersistenceUnitInfo> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getAnnotatedMember());
    }
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processEntityManagerBean(@Observes final ProcessBean<? extends EntityManager> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getBean());
    }
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processEntityManagerProducerMethod(@Observes final ProcessProducer<?, ? extends EntityManager> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getAnnotatedMember());
    }
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processEntityManagerFactoryBean(@Observes final ProcessBean<? extends EntityManagerFactory> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getBean());
    }
  }

  // See https://issues.jboss.org/browse/WELD-2461.
  // See https://issues.jboss.org/browse/CDI-538.
  // See https://issues.jboss.org/browse/CDI-438.
  private final void processEntityManagerFactoryProducerMethod(@Observes final ProcessProducer<?, ? extends EntityManagerFactory> event) {
    if (event != null) {
      this.addPersistenceUnitName(event.getAnnotatedMember());
    }
  }

  private final void addPersistenceUnitName(final Annotated annotated) {
    if (annotated != null) {
      this.addPersistenceUnitName(annotated.getAnnotations());
    }
  }
  
  private final void addPersistenceUnitName(final Bean<?> bean) {
    if (bean != null) {
      this.addPersistenceUnitName(bean.getQualifiers());
    }
  }

  private final void addPersistenceUnitName(final Set<? extends Annotation> qualifiers) {
    if (qualifiers != null && !qualifiers.isEmpty()) {
      String candidate = null;
      for (final Annotation qualifier : qualifiers) {
        assert qualifier != null;
        if (qualifier instanceof Named) {
          candidate = ((Named)qualifier).value();
          assert candidate != null;
          if (!candidate.isEmpty()) {
            this.persistenceUnitNames.add(candidate);
            break;
          }
        }
      }
    }
  }

  private final void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
    if (event != null && beanManager != null && !this.persistenceUnitNames.isEmpty()) {
      
      for (final String persistenceUnitName : this.persistenceUnitNames) {
        assert persistenceUnitName != null;

        final Named namedLiteral = NamedLiteral.of(persistenceUnitName);
        assert namedLiteral != null;

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
              final PersistenceUnitInfo persistenceUnitInfo;
              if (!beans.isEmpty()) {
                // If there any appropriately-named
                // PersistenceUnitInfo beans, then resolve them and
                // get the sole instance (or die trying).
                bean = beanManager.resolve(beans);
                assert bean != null;
                persistenceUnitInfo = (PersistenceUnitInfo)beanManager.getReference(bean, PersistenceUnitInfo.class, beanManager.createCreationalContext(bean));
              } else {
                // There were no appropriately-named
                // PersistenceUnitInfo beans at all, so there's no way
                // to get a PersistenceUnitInfo instance.
                persistenceUnitInfo = null;
              }

              final EntityManagerFactory returnValue;
              if (persistenceUnitInfo == null) {
                // If there wasn't a PersistenceUnitInfo, then we're
                // just creating an application-managed
                // EntityManagerFactory as if "by hand".
                returnValue = createEntityManagerFactory(persistenceUnitName, providers);
              } else {
                // If there was a PersistenceUnitInfo, then we're
                // creating a container-managed EntityManagerFactory
                // using either RESOURCE_LOCAL or JTA transactions.
                returnValue = createEntityManagerFactory(persistenceUnitInfo, providers, beanManager);
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

              Context context = null;
              try {
                context = beanManager.getContext(TransactionScoped.class);
              } catch (final ContextNotActiveException contextNotActiveException) {

              }

              final EntityManager em;
              if (context == null) {
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
              if (em.isOpen() && !em.isJoinedToTransaction()) {
                // We close a Dependent-scoped EntityManager only if
                // we know that when we created it no JTA transaction
                // was in effect.  Otherwise this EntityManager is a
                // transaction-scoped EntityManager and we shouldn't
                // destroy it; the transaction going out of scope
                // should do that automatically.
                em.close();
              }
            });
                
      }
      
    }
  }

  private final void ensurePersistenceUnitInfosAreNamedProperly(@Observes final AfterDeploymentValidation event, final BeanManager beanManager) {
    if (event != null && beanManager != null && !this.persistenceUnitNames.isEmpty()) {

      for (final String persistenceUnitName : this.persistenceUnitNames) {
        assert persistenceUnitName != null;

        final Named namedLiteral = NamedLiteral.of(persistenceUnitName);
        assert namedLiteral != null;

        final Set<Bean<?>> beans = beanManager.getBeans(PersistenceUnitInfo.class, namedLiteral);
        assert beans != null;

        if (!beans.isEmpty()) {

          final Bean<?> bean = beanManager.resolve(beans);
          assert bean != null;

          final PersistenceUnitInfo persistenceUnitInfo = (PersistenceUnitInfo)beanManager.getReference(bean, PersistenceUnitInfo.class, beanManager.createCreationalContext(bean));
          if (persistenceUnitInfo != null && !persistenceUnitName.equals(persistenceUnitInfo.getPersistenceUnitName())) {
            event.addDeploymentProblem(new IllegalStateException("A PersistenceUnitInfo's name (" + persistenceUnitInfo.getPersistenceUnitName() + ") did not match its bean's related @Named qualifier value (" + persistenceUnitName + ")"));
          }
        }
        
      }
      this.persistenceUnitNames.clear();
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
    for (final PersistenceProvider provider : providers) {
      assert provider != null;
      final Map<String, Object> properties = new HashMap<>();
      properties.put("javax.persistence.bean.manager", beanManager);
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
