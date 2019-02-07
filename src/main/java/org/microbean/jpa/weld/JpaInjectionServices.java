/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean.
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

import java.lang.annotation.Annotation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;
import javax.persistence.SynchronizationType;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.jboss.weld.injection.spi.ResourceReference;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;

import org.microbean.development.annotation.Issue;

/**
 * A {@link org.jboss.weld.injection.spi.JpaInjectionServices}
 * implementation that integrates JPA functionality into Weld-based
 * CDI environments.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see org.jboss.weld.injection.spi.JpaInjectionServices
 */
@Issue(id = "WELD-2563", uri = "https://issues.jboss.org/browse/WELD-2563")
public final class JpaInjectionServices implements org.jboss.weld.injection.spi.JpaInjectionServices {


  /*
   * Static fields.
   */

  
  /*
   * Weld instantiates this class three times during normal execution
   * (see https://issues.jboss.org/browse/WELD-2563 for details).
   * Only one of those instances (the first, I think) is actually used
   * to produce EntityManagers and EntityManagerFactories; the other
   * two are discarded.  The static INSTANCE and UNDERWAY fields
   * ensure that truly only one instance processes all incoming calls.
   *
   * See the underway() method as well.
   */

  /**
   * The single officially sanctioned instance of this class.
   *
   * <p>This field may be {@code null}.</p>
   */
  @Issue(id = "WELD_2563", uri = "https://issues.jboss.org/browse/WELD-2563")
  static volatile JpaInjectionServices INSTANCE;

  @Issue(id = "WELD_2563", uri = "https://issues.jboss.org/browse/WELD-2563")
  private static volatile boolean UNDERWAY;


  /*
   * Instance fields.
   */

  
  private final Set<EntityManager> ems;

  // @GuardedBy("this")
  private volatile Map<String, EntityManagerFactory> emfs;


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link JpaInjectionServices}.
   */
  public JpaInjectionServices() {
    super();
    synchronized (JpaInjectionServices.class) {
      if (INSTANCE != null && UNDERWAY) {
        throw new IllegalStateException();
      }
      INSTANCE = this;
    }
    this.ems = ConcurrentHashMap.newKeySet();
  }

  @Issue(id = "WELD_2563", uri = "https://issues.jboss.org/browse/WELD-2563")
  private static synchronized final void underway() {
    assert INSTANCE != null;
    UNDERWAY = true;
  }

  /**
   * Called by the ({@code private}) {@code
   * JpaInjectionServicesExtension} class when a JTA transaction is
   * begun.
   *
   * <p>The Narayana CDI integration this class is often deployed with
   * will fire such events.  These events serve as an indication that
   * a call to {@link TransactionManager#begin()} has been made.</p>
   *
   * <p>{@link EntityManager}s created by this class will have their
   * {@link EntityManager#joinTransaction()} methods called if the
   * supplied object is non-{@code null}.</p>
   *
   * @param transaction an {@link Object} representing the
   * transaction; may be {@code null} in which case no action will be
   * taken
   */
  final void jtaTransactionBegun(final Object transaction) {
    if (this != INSTANCE) {
      INSTANCE.jtaTransactionBegun(transaction);
    } else if (transaction != null) {
      ems.forEach(em -> em.joinTransaction());
    }
  }

  /**
   * Returns a {@link ResourceReferenceFactory} whose {@link
   * ResourceReferenceFactory#createResource()} method will be invoked
   * appropriately by Weld later.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param the {@link InjectionPoint} annotated with {@link
   * PersistenceContext}; must not be {@code null}
   *
   * @return a non-{@code null} {@link ResourceReferenceFactory} whose
   * {@link ResourceReferenceFactory#createResource()} method will
   * create {@link EntityManager} instances
   *
   * @exception NullPointerException if {@code injectionPoint} is
   * {@code null}
   *
   * @see ResourceReferenceFactory#createResource()
   */
  @Override
  public final ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
    underway();
    final ResourceReferenceFactory<EntityManager> returnValue;
    if (this != INSTANCE) {
      returnValue = INSTANCE.registerPersistenceContextInjectionPoint(injectionPoint);
    } else {
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
      final SynchronizationType synchronizationType = persistenceContextAnnotation.synchronization();
      assert synchronizationType != null;    
      synchronized (this) {
        if (this.emfs == null) {
          this.emfs = new ConcurrentHashMap<>();
        }
      }
      returnValue = () -> new EntityManagerResourceReference(name, synchronizationType);
    }
    return returnValue;
  }

  /**
   * Returns a {@link ResourceReferenceFactory} whose {@link
   * ResourceReferenceFactory#createResource()} method will be invoked
   * appropriately by Weld later.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param the {@link InjectionPoint} annotated with {@link
   * PersistenceUnit}; must not be {@code null}
   *
   * @return a non-{@code null} {@link ResourceReferenceFactory} whose
   * {@link ResourceReferenceFactory#createResource()} method will
   * create {@link EntityManagerFactory} instances
   *
   * @exception NullPointerException if {@code injectionPoint} is
   * {@code null}
   *
   * @see ResourceReferenceFactory#createResource()
   */
  @Override
  public final ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint injectionPoint) {
    underway();
    final ResourceReferenceFactory<EntityManagerFactory> returnValue;
    if (this != INSTANCE) {
      returnValue = INSTANCE.registerPersistenceUnitInjectionPoint(injectionPoint);
    } else {
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
      returnValue = () -> new EntityManagerFactoryResourceReference(this.emfs, name);
    }
    return returnValue;
  }

  /**
   * Invoked by Weld automatically to clean up any resources held by
   * this class.
   */
  @Override
  public final void cleanup() {
    underway();
    if (this != INSTANCE) {
      INSTANCE.cleanup();
    } else {
      ems.clear();
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
  }

  /**
   * Calls the {@link
   * #registerPersistenceContextInjectionPoint(InjectionPoint)} method
   * and invokes {@link ResourceReference#getInstance()} on its return
   * value and returns the result.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param injectionPoint an {@link InjectionPoint} annotated with
   * {@link PersistenceContext}; must not be {@code null}
   *
   * @return a non-{@code null} {@link EntityManager}
   *
   * @see #registerPersistenceContextInjectionPoint(InjectionPoint)
   */
  @Deprecated
  @Override
  public final EntityManager resolvePersistenceContext(final InjectionPoint injectionPoint) {
    return this.registerPersistenceContextInjectionPoint(injectionPoint).createResource().getInstance();
  }

  /**
   * Calls the {@link
   * #registerPersistenceUnitInjectionPoint(InjectionPoint)} method
   * and invokes {@link ResourceReference#getInstance()} on its return
   * value and returns the result.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param injectionPoint an {@link InjectionPoint} annotated with
   * {@link PersistenceUnit}; must not be {@code null}
   *
   * @return a non-{@code null} {@link EntityManagerFactory}
   *
   * @see #registerPersistenceUnitInjectionPoint(InjectionPoint)
   */
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
                               final CDI<Object> cdi = CDI.current();
                               assert cdi != null;
                               final BeanManager beanManager = cdi.getBeanManager();
                               assert beanManager != null;
                               final Map<String, Object> properties = new HashMap<>();
                               properties.put("javax.persistence.bean.manager",
                                              beanManager);
                               Class<?> validatorFactoryClass = null;
                               try {
                                 validatorFactoryClass = Class.forName("javax.validation.ValidatorFactory");
                               } catch (final ClassNotFoundException classNotFoundException) {
                                 classNotFoundException.printStackTrace();
                               }
                               if (validatorFactoryClass != null) {
                                 final Bean<?> validatorFactoryBean =
                                   getValidatorFactoryBean(beanManager,
                                                           validatorFactoryClass);
                                 if (validatorFactoryBean != null) {
                                   properties.put("javax.validation.ValidatorFactory",
                                                  beanManager.getReference(validatorFactoryBean,
                                                                           validatorFactoryClass,
                                                                           beanManager.createCreationalContext(validatorFactoryBean)));
                                 }
                               }
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

  private final class EntityManagerResourceReference implements ResourceReference<EntityManager> {

    private final String name;

    private final SynchronizationType synchronizationType;

    // @GuardedBy("this")
    private EntityManager em;

    private EntityManagerResourceReference(final String name,
                                           final SynchronizationType synchronizationType) {
      super();
      this.name = Objects.requireNonNull(name);
      this.synchronizationType = Objects.requireNonNull(synchronizationType);
    }

    @Override
    public synchronized final EntityManager getInstance() {
      EntityManager returnValue = this.em;
      if (returnValue == null) {
        final PersistenceUnitInfo persistenceUnitInfo = getPersistenceUnitInfo(this.name);
        assert persistenceUnitInfo != null;
        final EntityManagerFactory emf;
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
          emf = getOrCreateEntityManagerFactory(emfs, null, this.name);
          assert emf != null;
          returnValue = emf.createEntityManager();
        } else {
          // JTA
          emf = getOrCreateEntityManagerFactory(emfs, persistenceUnitInfo, this.name);
          assert emf != null;
          returnValue = emf.createEntityManager(this.synchronizationType);
          ems.add(returnValue);
        }
        assert returnValue != null;
        this.em = returnValue;
      }
      return returnValue;
    }

    @Override
    public final void release() {
      final EntityManager em;
      synchronized (this) {
        em = this.em;
        this.em = null;
      }
      if (em != null) {
        if (em.isOpen()) {
          em.close();
        }
        ems.remove(em);        
      }
    }

  }

  private static final Bean<?> getValidatorFactoryBean(final BeanManager beanManager,
                                                       final Class<?> validatorFactoryClass) {
    return getValidatorFactoryBean(beanManager, validatorFactoryClass, null);
  }
  
  private static final Bean<?> getValidatorFactoryBean(final BeanManager beanManager,
                                                       final Class<?> validatorFactoryClass,
                                                       final Set<Annotation> qualifiers) {
    Bean<?> returnValue = null;
    if (beanManager != null && validatorFactoryClass != null) {
      final Set<Bean<?>> beans;
      if (qualifiers == null) {
        beans = beanManager.getBeans(validatorFactoryClass);
      } else {
        beans = beanManager.getBeans(validatorFactoryClass, qualifiers.toArray(new Annotation[qualifiers.size()]));
      }
      if (beans != null && !beans.isEmpty()) {
        returnValue = beanManager.resolve(beans);
      }
    }
    return returnValue;
  }

}
