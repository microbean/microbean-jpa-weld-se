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

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;

import javax.inject.Singleton;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.microbean.jpa.jaxb.Persistence;

public class Extension implements javax.enterprise.inject.spi.Extension {

  public Extension() {
    super();
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
                                                    null,
                                                    null); // TODO: nulls need to be datasource providers
          for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
            event.addBean()
              .types(Collections.singleton(PersistenceUnitInfo.class))
              .scope(Singleton.class)
              .addQualifiers(NamedLiteral.of(persistenceUnitInfo.getPersistenceUnitName()))
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

}
