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

import javax.inject.Singleton;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

public class Extension implements javax.enterprise.inject.spi.Extension {

  public Extension() {
    super();
  }

  private final void addPersistenceUnitInfoBeans(@Observes final AfterBeanDiscovery event) throws IOException, JAXBException {
    if (event != null) {
      final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
      assert tccl != null;
      final Enumeration<URL> urls = tccl.getResources("META-INF/persistence.xml");
      if (urls != null) {
        final JAXBContext jaxbContext = JAXBContext.newInstance("org.microbean.jpa.jaxb");
        assert jaxbContext != null;
        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        assert unmarshaller != null;        
        while (urls.hasMoreElements()) {
          final URL persistenceXmlUrl = urls.nextElement();
          if (persistenceXmlUrl != null) {
            final URL rootUrl = new URL(persistenceXmlUrl, "../..");
            final org.microbean.jpa.jaxb.Persistence persistence = (org.microbean.jpa.jaxb.Persistence)unmarshaller.unmarshal(persistenceXmlUrl);
            assert persistence != null;
            
            final Collection<? extends PersistenceUnitInfo> persistenceUnitInfos =
              PersistenceUnitInfoBean.fromPersistence(persistence,
                                                      rootUrl,
                                                      null,
                                                      null); // TODO: nulls need to be datasource providers
            assert persistenceUnitInfos != null;
            for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
              assert persistenceUnitInfo != null;
          
              final String name = persistenceUnitInfo.getPersistenceUnitName();
              assert name != null;
              
              System.out.println("*** adding bean: " + name);
              
              event.addBean()
                .types(Collections.singleton(PersistenceUnitInfo.class))
                .scope(Singleton.class)
                .addQualifiers(NamedLiteral.of(name))
                .createWith(cc -> persistenceUnitInfo);
              
            }
          }
        }
      }
    }
  }

}
