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
package org.microbean.jpa.org.hibernate.engine.transaction.jta.platform;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.inject.spi.CDI;

import javax.inject.Inject;

import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.hibernate.engine.jndi.spi.JndiService;

import org.hibernate.engine.transaction.jta.platform.internal.AbstractJtaPlatform;

import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatformProvider;

@ApplicationScoped
public class CDISEJtaPlatform extends AbstractJtaPlatform {

  private final TransactionManager transactionManager;

  private final UserTransaction userTransaction;
  
  private static final long serialVersionUID = 1L;
  
  @Inject
  public CDISEJtaPlatform(final TransactionManager transactionManager,
                          final UserTransaction userTransaction) {
    super();
    this.transactionManager = Objects.requireNonNull(transactionManager);
    this.userTransaction = Objects.requireNonNull(userTransaction);
  }

  @Override
  protected JndiService jndiService() {
    throw new UnsupportedOperationException();
	}
  
  @Override
  protected UserTransaction locateUserTransaction() {
    return this.userTransaction;
  }

  @Override
  protected TransactionManager locateTransactionManager() {
    return this.transactionManager;
  }
  
}
