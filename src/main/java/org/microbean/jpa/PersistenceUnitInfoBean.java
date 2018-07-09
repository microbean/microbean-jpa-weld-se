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

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;

import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import javax.sql.DataSource;

public class PersistenceUnitInfoBean implements PersistenceUnitInfo {

  private final ClassLoader classLoader;

  private final boolean excludeUnlistedClasses;
  
  private final List<URL> jarFileUrls;

  private final DataSource jtaDataSource;
  
  private final List<String> managedClassNames;

  private final List<String> mappingFileNames;
  
  private final DataSource nonJtaDataSource;
  
  private final String persistenceProviderClassName;
  
  private final String persistenceUnitName;
  
  private final URL persistenceUnitRootUrl;
  
  private final String persistenceXMLSchemaVersion;

  private final Properties properties;

  private final SharedCacheMode sharedCacheMode;

  private final ClassLoader tempClassLoader;

  private final PersistenceUnitTransactionType transactionType;
  
  private final ValidationMode validationMode;

  public PersistenceUnitInfoBean(final ClassLoader classLoader,
                                 final boolean excludeUnlistedClasses,
                                 final Collection<? extends URL> jarFileUrls,
                                 final DataSource jtaDataSource,
                                 final Collection<? extends String> managedClassNames,
                                 final Collection<? extends String> mappingFileNames,
                                 final DataSource nonJtaDataSource,
                                 final String persistenceProviderClassName,
                                 final String persistenceUnitName,
                                 final URL persistenceUnitRootUrl,
                                 final String persistenceXMLSchemaVersion,
                                 final Properties properties,
                                 final SharedCacheMode sharedCacheMode,
                                 final ClassLoader tempClassLoader,
                                 final PersistenceUnitTransactionType transactionType,
                                 final ValidationMode validationMode) {
    super();
    this.classLoader = classLoader;
    this.excludeUnlistedClasses = excludeUnlistedClasses;
    if (jarFileUrls == null || jarFileUrls.isEmpty()) {
      this.jarFileUrls = Collections.emptyList();
    } else {
      this.jarFileUrls = Collections.unmodifiableList(new ArrayList<>(jarFileUrls));
    }
    this.jtaDataSource = jtaDataSource;
    if (managedClassNames == null || managedClassNames.isEmpty()) {
      this.managedClassNames = Collections.emptyList();
    } else {
      this.managedClassNames = Collections.unmodifiableList(new ArrayList<>(managedClassNames));
    }
    if (mappingFileNames == null || mappingFileNames.isEmpty()) {
      this.mappingFileNames = Collections.emptyList();
    } else {
      this.mappingFileNames = Collections.unmodifiableList(new ArrayList<>(mappingFileNames));
    }
    this.nonJtaDataSource = nonJtaDataSource;
    this.persistenceProviderClassName = persistenceProviderClassName;
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceUnitRootUrl = persistenceUnitRootUrl;
    this.persistenceXMLSchemaVersion = persistenceXMLSchemaVersion;
    if (properties == null) {
      this.properties = new Properties();
    } else {
      this.properties = properties;
    }
    if (sharedCacheMode == null) {
      this.sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    } else {
      this.sharedCacheMode = sharedCacheMode;
    }
    this.tempClassLoader = tempClassLoader;
    this.transactionType = Objects.requireNonNull(transactionType);
    if (validationMode == null) {
      this.validationMode = ValidationMode.AUTO;
    } else {
      this.validationMode = validationMode;
    }
  }

  @Override
  public List<URL> getJarFileUrls() {
    return this.jarFileUrls;
  }
  
  @Override
  public URL getPersistenceUnitRootUrl() {
    return this.persistenceUnitRootUrl;
  }

  @Override
  public List<String> getManagedClassNames() {
    return this.managedClassNames;
  }

  @Override
  public boolean excludeUnlistedClasses() {
    return this.excludeUnlistedClasses;
  }
  
  @Override
  public SharedCacheMode getSharedCacheMode() {
    return this.sharedCacheMode;
  }
  
  @Override
  public ValidationMode getValidationMode() {
    return this.validationMode;
  }
  
  @Override
  public Properties getProperties() {
    return this.properties;
  }
  
  @Override
  public ClassLoader getClassLoader() {
    ClassLoader cl = this.classLoader;
    if (cl == null) {
      cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = this.getClass().getClassLoader();
      }
    }
    return cl;
  }

  @Override
  public String getPersistenceXMLSchemaVersion() {
    return this.persistenceXMLSchemaVersion;
  }
  
  @Override
  public ClassLoader getNewTempClassLoader() {
    ClassLoader cl = this.tempClassLoader;
    if (cl == null) {
      cl = this.getClassLoader();
      if (cl == null) {
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
          cl = this.getClass().getClassLoader();
        }
      }
    }
    return cl;
  }

  @Override
  public void addTransformer(final ClassTransformer classTransformer) {
    // TODO: implement, maybe.  This is a very, very weird method. See
    // https://github.com/javaee/glassfish/blob/168ce449c4ea0826842ab4129e83c4a700750970/appserver/persistence/jpa-container/src/main/java/org/glassfish/persistence/jpa/ServerProviderContainerContractInfo.java#L91.
    // 99.99% of the implementations of this method on Github do
    // nothing.  The general idea seems to be that at
    // createContainerEntityManagerFactory() time (see
    // PersistenceProvider), the *provider* (e.g. EclipseLink) will
    // call this method, which will "tunnel", somehow, the supplied
    // ClassTransformer "through" "into" the container (in our case
    // Weld) somehow such that at class load time this
    // ClassTransformer will be called.
    //
    // So semantically addTransformer is really a method on the whole
    // container ecosystem, and the JPA provider is saying, "Here,
    // container ecosystem, please make this ClassTransformer be used
    // when you, not I, load entity classes."
    //
    // There is also an unspoken assumption that this method will be
    // called only once, if ever.
  }

  @Override
  public String getPersistenceUnitName() {
    return this.persistenceUnitName;
  }

  @Override
  public String getPersistenceProviderClassName() {
    return this.persistenceProviderClassName;
  }

  @Override
  public PersistenceUnitTransactionType getTransactionType() {
    return this.transactionType;
  }

  @Override
  public DataSource getJtaDataSource() {
    return this.jtaDataSource;
  }

  @Override
  public DataSource getNonJtaDataSource() {
    return this.nonJtaDataSource;
  }

  @Override
  public List<String> getMappingFileNames() {
    return this.mappingFileNames;
  }
  
}
