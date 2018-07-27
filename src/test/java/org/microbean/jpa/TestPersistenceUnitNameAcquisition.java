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


import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;

import javax.inject.Inject;
import javax.inject.Named;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.transaction.UserTransaction;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

import static org.microbean.main.Main.main;

@ApplicationScoped
public class TestPersistenceUnitNameAcquisition {

  @Inject
  @Named("test")
  private PersistenceUnitInfo persistenceUnitInfo;

  @Inject
  @Named("test")
  private EntityManagerFactory emf;

  @Inject
  private UserTransaction injectedUserTransaction;

  @Inject
  private TestPersistenceUnitNameAcquisition self;

  @Inject
  @Default
  @Named("test")
  private EntityManager testEm;
  
  public TestPersistenceUnitNameAcquisition() {
    super();
  }

  @Produces
  @Named("test")
  private static final PersistenceUnitInfo producePersistenceUnitInfo() {
    return null; // TODO fixme
  }

  

  private final void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event, final UserTransaction userTransaction) {
    assertNotNull(this.emf);
    assertNotNull(userTransaction);
    assertNotNull(this.self);
    this.self.frobnicate();
  }

  @Transactional(TxType.REQUIRED)
  public void frobnicate() {
    System.out.println("*** frobnicating");
  }

  @Test
  public void testIt() throws Exception {
    main(null);
  }
  
}
