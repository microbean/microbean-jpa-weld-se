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
package org.microbean.jpa.org.eclipse.persistence.platform.server.cdi;

import java.util.concurrent.Executor;

import javax.enterprise.inject.Instance;

import javax.enterprise.inject.spi.CDI;

import javax.management.MBeanServer;

import javax.transaction.TransactionManager;

import org.eclipse.persistence.platform.server.JMXServerPlatformBase;

import org.eclipse.persistence.sessions.DatabaseSession;
import org.eclipse.persistence.sessions.JNDIConnector;

import org.eclipse.persistence.transaction.JTATransactionController;

/**
 * A {@link JMXServerPlatformBase} that arranges things such that CDI,
 * not JNDI, will be used to acquire a {@link TransactionManager} and
 * {@link MBeanServer}.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #getExternalTransactionControllerClass()
 */
public class CDISEPlatform extends JMXServerPlatformBase {


  /*
   * Instance fields.
   */
  

  private final Executor executor;

  private volatile Instance<MBeanServer> mBeanServerInstance;
  

  /*
   * Constructors.
   */

  
  /**
   * Creates a {@link CDISEPlatform}.
   *
   * @param session the {@link DatabaseSession} this platform will
   * wrap; must not be {@code null}
   *
   * @see JMXServerPlatformBase#JMXServerPlatformBase(DatabaseSession)
   */
  public CDISEPlatform(final DatabaseSession session) {
    super(session);
    final CDI<Object> cdi = CDI.current();
    assert cdi != null;
    if (!cdi.select(TransactionManager.class).isResolvable()) {
      this.disableJTA();
    }
    final Instance<Executor> executorInstance = cdi.select(Executor.class);
    if (executorInstance == null || !executorInstance.isResolvable()) {
      this.executor = null;
    } else {
      this.executor = executorInstance.get();
    }
  }


  /*
   * Instance methods.
   */

  
  @Override
  public boolean isRuntimeServicesEnabledDefault() {
    Instance<MBeanServer> instance = this.mBeanServerInstance;
    final boolean returnValue;
    if (instance == null) {
      instance = CDI.current().select(MBeanServer.class);
      assert instance != null;
      if (instance.isResolvable()) {
        this.mBeanServerInstance = instance;
        returnValue = true;
      } else {
        returnValue = false;
      }
    } else {
      returnValue = instance.isResolvable();
    }
    return returnValue;
  }
  
  @Override
  public MBeanServer getMBeanServer() {
    if (this.mBeanServer == null) {
      final Instance<MBeanServer> instance = this.mBeanServerInstance;
      if (instance != null && instance.isResolvable()) {
        this.mBeanServer = instance.get();
      }
    }
    return super.getMBeanServer();
  }
  
  @Override
  public void launchContainerRunnable(final Runnable runnable) {
    if (runnable != null && this.executor != null) {
      this.executor.execute(runnable);
    } else {
      super.launchContainerRunnable(runnable);
    }
  }
  
  /**
   * Returns a non-{@code null} {@link Class} that extends {@link
   * org.eclipse.persistence.transaction.AbstractTransactionController}.
   *
   * @return a non-{@code null} {@link Class} that extends {@link
   * org.eclipse.persistence.transaction.AbstractTransactionController}
   *
   * @see org.eclipse.persistence.transaction.AbstractTransactionController
   */
  @Override
  public final Class<?> getExternalTransactionControllerClass() {
    if (this.externalTransactionControllerClass == null) {
      this.externalTransactionControllerClass = TransactionController.class;
    }
    return this.externalTransactionControllerClass;
  }

  /**
   * Returns {@link JNDIConnector#UNDEFINED_LOOKUP} when invoked.
   *
   * @return {@link JNDIConnector#UNDEFINED_LOOKUP}
   */
  @Override
  public final int getJNDIConnectorLookupType() {
    return JNDIConnector.UNDEFINED_LOOKUP;
  }

  
  /*
   * Inner and nested classes.
   */


  /**
   * A {@link JTATransactionController} whose {@link
   * #acquireTransactionManager()} uses CDI, not JNDI, to return a
   * {@link TransactionManager} instance.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see #acquireTransactionManager()
   *
   * @see JTATransactionController
   */
  public static final class TransactionController extends JTATransactionController {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link TransactionController}.
     */
    public TransactionController() {
      super();
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns a non-{@code null} {@link TransactionManager}.
     *
     * @return a non-{@code null} {@link TransactionManager}
     */
    @Override
    protected final TransactionManager acquireTransactionManager() {
      return CDI.current().select(TransactionManager.class).get();
    }

  }
  
}
