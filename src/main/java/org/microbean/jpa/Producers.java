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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;

import javax.transaction.TransactionScoped;

import org.microbean.jpa.annotation.JTA;

@ApplicationScoped
final class Producers {

  private Producers() {
    super();
  }

  @Produces
  @ApplicationScoped
  private static final PersistenceProviderResolver producePersistenceProviderResolver() {
    return PersistenceProviderResolverHolder.getPersistenceProviderResolver();
  }

  @Produces
  @ApplicationScoped
  private static final List<PersistenceProvider> producePersistenceProviders(final PersistenceProviderResolver resolver) {
    return Objects.requireNonNull(resolver).getPersistenceProviders();
  }
  
  @Produces
  @ApplicationScoped
  private static final PersistenceProvider getPersistenceProvider(final Collection<? extends PersistenceProvider> providers) {
    return Objects.requireNonNull(providers).iterator().next();
  }

}
