/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;

import javax.enterprise.event.Observes;

import javax.transaction.TransactionScoped;

/**
 * A bean housing an observer method that alerts a {@link
 * JpaInjectionServices} instance when a JTA transaction is available.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see JpaInjectionServices
 */
@ApplicationScoped
final class TransactionObserver {

  private TransactionObserver() {
    super();
  }

  private static final void jtaTransactionBegun(@Observes @Initialized(TransactionScoped.class) final Object event,
                                                final JpaInjectionServices services) {
    if (services != null) {
      services.jtaTransactionBegun();
    }
  }
  
}
