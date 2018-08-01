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
import javax.persistence.spi.PersistenceProviderResolverHolder;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.jboss.weld.injection.spi.ResourceReference;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;

public class JpaInjectionServices implements org.jboss.weld.injection.spi.JpaInjectionServices {

  private final Map<String, EntityManagerFactory> emfs;
  
  public JpaInjectionServices() {
    super();
    this.emfs = new ConcurrentHashMap<>();
  }

  public ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
    System.out.println("*** registerPersistenceContextInjectionPoint");
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
    return () -> new EntityManagerResourceReference(this.emfs, name);
  }

  @Override
  public ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint injectionPoint) {
    final PersistenceUnit persistenceUnitAnnotation =
      Objects.requireNonNull(injectionPoint).getAnnotated().getAnnotation(PersistenceUnit.class);
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
    return () -> new EntityManagerFactoryResourceReference(this.emfs, name);
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

  @Override
  public void cleanup() {
    if (!this.emfs.isEmpty()) {
      final Collection<? extends Entry<? extends String, ? extends EntityManagerFactory>> entries = this.emfs.entrySet();
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

  private static final PersistenceProvider getPersistenceProvider(final PersistenceUnitInfo persistenceUnitInfo) {
    final String providerClassName = Objects.requireNonNull(persistenceUnitInfo).getPersistenceProviderClassName();
    final PersistenceProvider persistenceProvider;
    if (providerClassName == null) {
      persistenceProvider =
        PersistenceProviderResolverHolder.getPersistenceProviderResolver().getPersistenceProviders().iterator().next();
    } else {
      try {
        persistenceProvider = (PersistenceProvider)Class.forName(providerClassName, true, Thread.currentThread().getContextClassLoader()).newInstance();
      } catch (final ReflectiveOperationException exception) {
        throw new PersistenceException(exception.getMessage(), exception);
      }
    }
    return persistenceProvider;
  }

  private static final PersistenceUnitInfo getPersistenceUnitInfo(final String name) {
    return CDI.current().select(PersistenceUnitInfo.class, NamedLiteral.of(Objects.requireNonNull(name))).get();
  }

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
      final EntityManagerFactory emf;
      if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
        emf = this.emfs.computeIfAbsent(this.name, n -> Persistence.createEntityManagerFactory(this.name));
      } else {
        final PersistenceProvider persistenceProvider = getPersistenceProvider(persistenceUnitInfo);
        assert persistenceProvider != null;
        emf = this.emfs.computeIfAbsent(this.name,
                                        n -> {
                                          final Map<String, Object> properties = new HashMap<>();
                                          properties.put("javax.persistence.bean.manager",
                                                         CDI.current().getBeanManager());
                                          return persistenceProvider.createContainerEntityManagerFactory(persistenceUnitInfo, properties);
                                        });
      }
      return emf;
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

    private EntityManager em;

    private EntityManagerResourceReference(final Map<String, EntityManagerFactory> emfs,
                                           final String name) {
      super();
      this.emfs = Objects.requireNonNull(emfs);
      this.name = Objects.requireNonNull(name);
    }
    
    @Override
    public final EntityManager getInstance() {
      System.out.println("*** getInstance (EntityManager)");
      if (this.em == null) {
        final PersistenceUnitInfo persistenceUnitInfo = getPersistenceUnitInfo(this.name);
        assert persistenceUnitInfo != null;
        final EntityManagerFactory emf;
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
          emf = this.emfs.computeIfAbsent(this.name, n -> Persistence.createEntityManagerFactory(this.name));
        } else {
          final PersistenceProvider persistenceProvider = getPersistenceProvider(persistenceUnitInfo);
          assert persistenceProvider != null;
          emf = this.emfs.computeIfAbsent(this.name,
                                          n -> {
                                            final Map<String, Object> properties = new HashMap<>();
                                            properties.put("javax.persistence.bean.manager",
                                                           CDI.current().getBeanManager());
                                            return persistenceProvider.createContainerEntityManagerFactory(persistenceUnitInfo, properties);
                                          });
        }
        assert emf != null;
        this.em = emf.createEntityManager();
      }
      return this.em;
    }

    @Override
    public final void release() {
      if (this.em != null && this.em.isOpen()) {
        this.em.close();
      }
    }
    
  }
  
}
