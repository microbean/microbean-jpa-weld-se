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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.jboss.weld.injection.spi.ResourceReference;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;

public final class JpaInjectionServices implements org.jboss.weld.injection.spi.JpaInjectionServices {

  private volatile Map<String, EntityManagerFactory> emfs;

  public JpaInjectionServices() {
    super();
  }

  @Override
  public final ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
    Objects.requireNonNull(injectionPoint);
    final Annotated annotatedMember = injectionPoint.getAnnotated();
    assert annotatedMember != null;
    final PersistenceContext persistenceContextAnnotation = annotatedMember.getAnnotation(PersistenceContext.class);
    if (persistenceContextAnnotation == null) {
      throw new IllegalArgumentException("injectionPoint.getAnnotated().getAnnotation(PersistenceContext.class) == null");
    }
    final String name;
    final String n = persistenceContextAnnotation.unitName();
    if (n.isEmpty()) {
      if (annotatedMember instanceof AnnotatedField) {
        name = ((AnnotatedField<?>)annotatedMember).getJavaMember().getName();
      } else {
        name = n;
      }
    } else {
      name = n;
    }
    synchronized (this) {
      if (this.emfs == null) {
        this.emfs = new ConcurrentHashMap<>();
      }
    }
    return () -> new EntityManagerResourceReference(this.emfs, name);
  }

  @Override
  public final ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint injectionPoint) {
    Objects.requireNonNull(injectionPoint);
    final Annotated annotatedMember = injectionPoint.getAnnotated();
    assert annotatedMember != null;
    final PersistenceUnit persistenceUnitAnnotation = annotatedMember.getAnnotation(PersistenceUnit.class);
    if (persistenceUnitAnnotation == null) {
      throw new IllegalArgumentException("injectionPoint.getAnnotated().getAnnotation(PersistenceUnit.class) == null");
    }
    final String name;
    final String n = persistenceUnitAnnotation.unitName();
    if (n.isEmpty()) {
      if (annotatedMember instanceof AnnotatedField) {
        name = ((AnnotatedField<?>)annotatedMember).getJavaMember().getName();
      } else {
        name = n;
      }
    } else {
      name = n;
    }
    synchronized (this) {
      if (this.emfs == null) {
        this.emfs = new ConcurrentHashMap<>();
      }
    }
    return () -> new EntityManagerFactoryResourceReference(this.emfs, name);
  }

  @Override
  public final void cleanup() {
    final Map<? extends String, ? extends EntityManagerFactory> emfs = this.emfs;
    if (emfs != null && !emfs.isEmpty()) {
      final Collection<? extends Entry<? extends String, ? extends EntityManagerFactory>> entries = emfs.entrySet();
      assert entries != null;
      assert !entries.isEmpty();
      final Iterator<? extends Entry<? extends String, ? extends EntityManagerFactory>> iterator = entries.iterator();
      assert iterator != null;
      assert iterator.hasNext();
      while (iterator.hasNext()) {
        final Entry<? extends String, ? extends EntityManagerFactory> entry = iterator.next();
        assert entry != null;
        final EntityManagerFactory emf = entry.getValue();
        assert emf != null;
        if (emf.isOpen()) {
          emf.close();
        }
        iterator.remove();
      }
    }
  }
  
  @Deprecated
  @Override
  public final EntityManager resolvePersistenceContext(final InjectionPoint injectionPoint) {
    return this.registerPersistenceContextInjectionPoint(injectionPoint).createResource().getInstance();
  }

  @Deprecated
  @Override
  public final EntityManagerFactory resolvePersistenceUnit(final InjectionPoint injectionPoint) {
    return this.registerPersistenceUnitInjectionPoint(injectionPoint).createResource().getInstance();
  }
  

  /*
   * Static methods.
   */

  
  private static final PersistenceProvider getPersistenceProvider(final PersistenceUnitInfo persistenceUnitInfo) {
    final String providerClassName = Objects.requireNonNull(persistenceUnitInfo).getPersistenceProviderClassName();
    final PersistenceProvider persistenceProvider;
    if (providerClassName == null) {
      persistenceProvider = CDI.current().select(PersistenceProvider.class).get();
    } else {
      try {
        persistenceProvider =
          (PersistenceProvider)CDI.current().select(Class.forName(providerClassName,
                                                                  true,
                                                                  Thread.currentThread().getContextClassLoader())).get();
      } catch (final ReflectiveOperationException exception) {
        throw new PersistenceException(exception.getMessage(), exception);
      }
    }
    return persistenceProvider;
  }

  private static final PersistenceUnitInfo getPersistenceUnitInfo(final String name) {
    return CDI.current().select(PersistenceUnitInfo.class,
                                NamedLiteral.of(Objects.requireNonNull(name))).get();
  }

  private static final EntityManagerFactory getOrCreateEntityManagerFactory(final Map<String, EntityManagerFactory> emfs,
                                                                            final PersistenceUnitInfo persistenceUnitInfo,
                                                                            final String name) {
    Objects.requireNonNull(emfs);
    Objects.requireNonNull(name);
    final EntityManagerFactory returnValue;
    if (persistenceUnitInfo == null) {
      returnValue =
        emfs.computeIfAbsent(name,
                             n -> Persistence.createEntityManagerFactory(n));

    } else {
      final PersistenceProvider persistenceProvider = getPersistenceProvider(persistenceUnitInfo);
      assert persistenceProvider != null;
      returnValue =
        emfs.computeIfAbsent(name,
                             n -> {
                               final Map<String, Object> properties = new HashMap<>();
                               properties.put("javax.persistence.bean.manager",
                                              CDI.current().getBeanManager());
                               return
                                 persistenceProvider.createContainerEntityManagerFactory(persistenceUnitInfo,
                                                                                         properties);
                             });
    }
    return returnValue;
  }


  /*
   * Inner and nested classes.
   */


  private static final class EntityManagerFactoryResourceReference implements ResourceReference<EntityManagerFactory> {

    private final Map<String, EntityManagerFactory> emfs;

    private final String name;

    private EntityManagerFactoryResourceReference(final Map<String, EntityManagerFactory> emfs,
                                                  final String name) {
      super();
      this.emfs = Objects.requireNonNull(emfs);
      this.name = Objects.requireNonNull(name);
    }

    @Override
    public final EntityManagerFactory getInstance() {
      final PersistenceUnitInfo persistenceUnitInfo = getPersistenceUnitInfo(this.name);
      assert persistenceUnitInfo != null;
      final EntityManagerFactory returnValue;
      if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
        returnValue = getOrCreateEntityManagerFactory(emfs, null, name);
      } else {
        returnValue = getOrCreateEntityManagerFactory(emfs, persistenceUnitInfo, name);
      }
      return returnValue;
    }

    @Override
    public final void release() {
      final EntityManagerFactory emf = this.emfs.remove(this.name);
      if (emf != null && emf.isOpen()) {
        emf.close();
      }
    }
  }

  private static final class EntityManagerResourceReference implements ResourceReference<EntityManager> {

    private final Map<String, EntityManagerFactory> emfs;

    private final String name;

    private volatile EntityManager em;

    private EntityManagerResourceReference(final Map<String, EntityManagerFactory> emfs,
                                           final String name) {
      super();
      this.emfs = Objects.requireNonNull(emfs);
      this.name = Objects.requireNonNull(name);
    }

    @Override
    public final EntityManager getInstance() {
      EntityManager returnValue = this.em;
      if (returnValue == null) {
        final PersistenceUnitInfo persistenceUnitInfo = getPersistenceUnitInfo(this.name);
        assert persistenceUnitInfo != null;
        final EntityManagerFactory emf;
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
          emf = getOrCreateEntityManagerFactory(this.emfs, null, this.name);
        } else {
          emf = getOrCreateEntityManagerFactory(this.emfs, persistenceUnitInfo, this.name);
        }
        assert emf != null;
        returnValue = emf.createEntityManager();
        assert returnValue != null;
        this.em = returnValue;
      }
      return returnValue;
    }

    @Override
    public final void release() {
      final EntityManager em = this.em;
      if (em != null && em.isOpen()) {
        em.close();
      }
    }

  }

}
