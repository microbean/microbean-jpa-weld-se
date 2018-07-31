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

import javax.persistence.spi.PersistenceUnitInfo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import org.microbean.jpa.jaxb.Persistence;

import static org.junit.Assert.assertNotNull;

public class TestPersistenceXmlLoading {

  public TestPersistenceXmlLoading() {
    super();
  }

  @Test
  public void testLoading() throws JAXBException {
    final JAXBContext jaxbContext = JAXBContext.newInstance("org.microbean.jpa.jaxb");
    assertNotNull(jaxbContext);
    final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler());
    final Persistence p = (Persistence)unmarshaller.unmarshal(Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + "/persistence.xml"));
    assertNotNull(p);
    System.out.println(p.getPersistenceUnit());
  }

}
