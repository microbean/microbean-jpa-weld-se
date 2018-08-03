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
package org.microbean.jpa.weld;

import java.io.IOException;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import javax.inject.Singleton;

import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceUnit;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.sql.DataSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.microbean.jpa.jaxb.Persistence;

public class Extension implements javax.enterprise.inject.spi.Extension {

  private final Map<String, Set<Class<?>>> entityClassesByPersistenceUnitNames;
  
  public Extension() {
    super();
    this.entityClassesByPersistenceUnitNames = new HashMap<>();
  }

  private final void discoverManagedClasses(@Observes @WithAnnotations({ Converter.class, Entity.class, Embeddable.class, MappedSuperclass.class }) final ProcessAnnotatedType<?> event) {
    if (event != null) {
      final AnnotatedType<?> annotatedType = event.getAnnotatedType();
      if (annotatedType != null) {
        final Class<?> entityClass = annotatedType.getJavaClass();
        assert entityClass != null;
        final Set<PersistenceUnit> persistenceUnits = annotatedType.getAnnotations(PersistenceUnit.class);
        if (persistenceUnits == null || persistenceUnits.isEmpty()) {
          Set<Class<?>> entityClasses = this.entityClassesByPersistenceUnitNames.get("");
          if (entityClasses == null) {
            entityClasses = new HashSet<>();
            this.entityClassesByPersistenceUnitNames.put("", entityClasses);
          }
          entityClasses.add(entityClass);
        } else {
          for (final PersistenceUnit persistenceUnit : persistenceUnits) {
            String name = "";
            if (persistenceUnit != null) {
              name = persistenceUnit.unitName();
              assert name != null;
            }
            Set<Class<?>> entityClasses = this.entityClassesByPersistenceUnitNames.get(name);
            if (entityClasses == null) {
              entityClasses = new HashSet<>();
              this.entityClassesByPersistenceUnitNames.put(name, entityClasses);
            }
            entityClasses.add(entityClass);
          }
        }
        event.veto(); // entities can't be beans
      }
    }
  }

  private final void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, final BeanManager beanManager)
    throws IOException, JAXBException, ReflectiveOperationException {
    if (event != null && beanManager != null) {

      // Add a bean for PersistenceProviderResolver.
      final PersistenceProviderResolver resolver =
        PersistenceProviderResolverHolder.getPersistenceProviderResolver();
      event.addBean()
        .types(PersistenceProviderResolver.class)
        .scope(Singleton.class)
        .createWith(cc -> resolver);

      // Add a bean for each "generic" PersistenceProvider reachable
      // from the resolver.  (Any PersistenceUnitInfo may also specify
      // the class name of a PersistenceProvider whose class may not
      // be among those loaded by the resolver; we deal with those
      // later.)
      final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
      for (final PersistenceProvider provider : providers) {
        event.addBean()
          .addTransitiveTypeClosure(provider.getClass())
          .scope(Singleton.class)
          .createWith(cc -> provider);
      }

      // Discover all META-INF/persistence.xml resources, load them
      // using JAXB, and turn them into PersistenceUnitInfo instances,
      // and add beans for all of them.
      final Enumeration<URL> urls =
        Thread.currentThread().getContextClassLoader().getResources("META-INF/persistence.xml");
      if (urls != null && urls.hasMoreElements()) {
        final Unmarshaller unmarshaller =
          JAXBContext.newInstance("org.microbean.jpa.jaxb").createUnmarshaller();
        assert unmarshaller != null;
        while (urls.hasMoreElements()) {
          final URL url = urls.nextElement();
          final Collection<? extends PersistenceUnitInfo> persistenceUnitInfos =
            PersistenceUnitInfoBean.fromPersistence((Persistence)unmarshaller.unmarshal(url),
                                                    new URL(url, "../.."),
                                                    this.entityClassesByPersistenceUnitNames,
                                                    jtaDataSourceName -> this.getJtaDataSource(jtaDataSourceName, beanManager),
                                                    nonJtaDataSourceName -> this.getNonJtaDataSource(nonJtaDataSourceName, beanManager));
          for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
            assert persistenceUnitInfo != null;

            String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
            if (persistenceUnitName == null) {
              persistenceUnitName = "";
            }

            event.addBean()
              .types(Collections.singleton(PersistenceUnitInfo.class))
              .scope(Singleton.class)
              .addQualifiers(NamedLiteral.of(persistenceUnitName))
              .createWith(cc -> persistenceUnitInfo);

            final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
            if (providerClassName != null) {
              @SuppressWarnings("unchecked")
              final Class<? extends PersistenceProvider> c = (Class<? extends PersistenceProvider>)Class.forName(providerClassName, true, Thread.currentThread().getContextClassLoader());
              assert c != null;
              final Collection<?> beans = beanManager.getBeans(c);
              if (beans == null || beans.isEmpty()) {
                // The PersistenceProvider class in question is not
                // one we already loaded.  Try to add a bean for it
                // too.
                final PersistenceProvider provider = c.newInstance();
                event.addBean()
                  .addTransitiveTypeClosure(provider.getClass())
                  .scope(Singleton.class)
                  .createWith(cc -> provider);
              }
            }
            
          }
        }
      }
    }
  }

  private final DataSource getJtaDataSource(final String dataSourceName, final BeanManager beanManager) {
    Objects.requireNonNull(dataSourceName);
    Objects.requireNonNull(beanManager);
    final Bean<?> bean = beanManager.resolve(beanManager.getBeans(DataSource.class, NamedLiteral.of(dataSourceName)));
    DataSource returnValue = null;
    if (bean != null) {
      returnValue = (DataSource)beanManager.getReference(bean, DataSource.class, beanManager.createCreationalContext(bean));
    }
    return returnValue;
  }

  private final DataSource getNonJtaDataSource(final String dataSourceName, final BeanManager beanManager) {
    Objects.requireNonNull(dataSourceName);
    Objects.requireNonNull(beanManager);
    final Bean<?> bean = beanManager.resolve(beanManager.getBeans(DataSource.class, NamedLiteral.of(dataSourceName)));
    DataSource returnValue = null;
    if (bean != null) {
      returnValue = (DataSource)beanManager.getReference(bean, DataSource.class, beanManager.createCreationalContext(bean));
    }
    return returnValue;
  }

}
