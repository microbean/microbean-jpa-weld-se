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

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import java.util.function.Function;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;

import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import javax.sql.DataSource;

import org.microbean.jpa.jaxb.Persistence;
import org.microbean.jpa.jaxb.PersistenceUnitCachingType;
import org.microbean.jpa.jaxb.PersistenceUnitValidationModeType;
import org.microbean.jpa.jaxb.Persistence.PersistenceUnit;

public class PersistenceUnitInfoBean implements PersistenceUnitInfo {

  private ClassLoader classLoader;

  private boolean excludeUnlistedClasses;
  
  private List<URL> jarFileUrls;

  private Function<? super String, DataSource> jtaDataSourceProvider;
  
  private List<String> managedClassNames;

  private List<String> mappingFileNames;

  private Function<? super String, DataSource> nonJtaDataSourceProvider;
  
  private String persistenceProviderClassName;

  private String persistenceUnitName;
  
  private URL persistenceUnitRootUrl;
  
  private String persistenceXMLSchemaVersion;

  private Properties properties;

  private SharedCacheMode sharedCacheMode;

  private ClassLoader tempClassLoader;

  private PersistenceUnitTransactionType transactionType;
  
  private ValidationMode validationMode;

  PersistenceUnitInfoBean() {
    super();
  }

  public PersistenceUnitInfoBean(final ClassLoader classLoader,
                                 final boolean excludeUnlistedClasses,
                                 final Collection<? extends URL> jarFileUrls,
                                 final Function<? super String, DataSource> jtaDataSourceProvider,
                                 final Collection<? extends String> managedClassNames,
                                 final Collection<? extends String> mappingFileNames,
                                 final Function<? super String, DataSource> nonJtaDataSourceProvider,
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
    if (jtaDataSourceProvider == null) {
      this.jtaDataSourceProvider = name -> null;
    } else {
      this.jtaDataSourceProvider = jtaDataSourceProvider;
    }
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
    if (nonJtaDataSourceProvider == null) {
      this.nonJtaDataSourceProvider = name -> null;
    } else {
      this.nonJtaDataSourceProvider = nonJtaDataSourceProvider;
    }
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
    return this.jtaDataSourceProvider.apply(this.getPersistenceUnitName());
  }

  @Override
  public DataSource getNonJtaDataSource() {
    return this.nonJtaDataSourceProvider.apply(this.getPersistenceUnitName());
  }

  @Override
  public List<String> getMappingFileNames() {
    return this.mappingFileNames;
  }

  static final Collection<? extends PersistenceUnitInfoBean> fromPersistence(final Persistence persistence,
                                                                             final URL rootUrl,
                                                                             final Map<? extends String, ? extends Set<? extends Class<?>>> classes,
                                                                             final Function<? super String, DataSource> jtaDataSourceProvider,
                                                                             final Function<? super String, DataSource> nonJtaDataSourceProvider)
    throws MalformedURLException {
    Objects.requireNonNull(rootUrl);
    final Collection<PersistenceUnitInfoBean> returnValue;
    if (persistence == null) {
      returnValue = Collections.emptySet();
    } else {
      final Collection<? extends PersistenceUnit> persistenceUnits = persistence.getPersistenceUnit();
      if (persistenceUnits == null || persistenceUnits.isEmpty()) {
        returnValue = Collections.emptySet();
      } else {
        returnValue = new ArrayList<>();
        for (final PersistenceUnit persistenceUnit : persistenceUnits) {
          assert persistenceUnit != null;
          returnValue.add(fromPersistenceUnit(persistenceUnit,
                                              rootUrl,
                                              classes,
                                              jtaDataSourceProvider,
                                              nonJtaDataSourceProvider));
        }
      }
    }
    return returnValue;
  }
  
  static final PersistenceUnitInfoBean fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                                                           final URL rootUrl,
                                                           final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                                                           final Function<? super String, DataSource> jtaDataSourceProvider,
                                                           final Function<? super String, DataSource> nonJtaDataSourceProvider)
    throws MalformedURLException {
    Objects.requireNonNull(persistenceUnit);
    Objects.requireNonNull(rootUrl);
    PersistenceUnitInfoBean returnValue = null;
    if (persistenceUnit != null) {
      final Collection<? extends String> jarFiles = persistenceUnit.getJarFile();
      final List<URL> jarFileUrls = new ArrayList<>();
      for (final String jarFile : jarFiles) {
        if (jarFile != null) {
          // TODO: probably won't work if rootUrl is, say, a jar URL
          jarFileUrls.add(new URL(rootUrl, jarFile));
        }        
      }
      
      final Collection<? extends String> mappingFiles = persistenceUnit.getMappingFile();

      final Properties properties = new Properties();
      final PersistenceUnit.Properties persistenceUnitProperties = persistenceUnit.getProperties();
      if (persistenceUnitProperties != null) {
        final Collection<? extends PersistenceUnit.Properties.Property> propertyInstances = persistenceUnitProperties.getProperty();
        if (propertyInstances != null && !propertyInstances.isEmpty()) {
          for (final PersistenceUnit.Properties.Property property : propertyInstances) {
            assert property != null;
            properties.setProperty(property.getName(), property.getValue());
          }
        }
      }

      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      final Collection<String> classes = persistenceUnit.getClazz();
      assert classes != null;
      String name = persistenceUnit.getName();
      if (name == null) {
        name = "";
      }
      final Boolean excludeUnlistedClasses = persistenceUnit.isExcludeUnlistedClasses();
      if (!Boolean.TRUE.equals(excludeUnlistedClasses)) {
        if (unlistedClasses != null && !unlistedClasses.isEmpty()) {
          Collection<? extends Class<?>> myUnlistedClasses = unlistedClasses.get(name);
          if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
            for (final Class<?> unlistedClass : myUnlistedClasses) {
              if (unlistedClass != null) {
                classes.add(unlistedClass.getName());
              }
            }
          }
          // Also add "default" ones
          if (!name.isEmpty()) {
            myUnlistedClasses = unlistedClasses.get("");
            if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
              for (final Class<?> unlistedClass : myUnlistedClasses) {
                if (unlistedClass != null) {
                  classes.add(unlistedClass.getName());
                }
              }
            }
          }
        }
      }
      
      final SharedCacheMode sharedCacheMode;
      final PersistenceUnitCachingType persistenceUnitCachingType = persistenceUnit.getSharedCacheMode();
      if (persistenceUnitCachingType == null) {
        sharedCacheMode = SharedCacheMode.UNSPECIFIED;
      } else {
        sharedCacheMode = SharedCacheMode.valueOf(persistenceUnitCachingType.name());
      }

      final PersistenceUnitTransactionType transactionType;
      final org.microbean.jpa.jaxb.PersistenceUnitTransactionType persistenceUnitTransactionType = persistenceUnit.getTransactionType();
      if (persistenceUnitTransactionType == null) {
        transactionType = PersistenceUnitTransactionType.JTA; // I guess
      } else {
        transactionType = PersistenceUnitTransactionType.valueOf(persistenceUnitTransactionType.name());
      }

      final ValidationMode validationMode;
      final PersistenceUnitValidationModeType validationModeType = persistenceUnit.getValidationMode();
      if (validationModeType == null) {
        validationMode = ValidationMode.AUTO;
      } else {
        validationMode = ValidationMode.valueOf(validationModeType.name());
      }
      
      returnValue = new PersistenceUnitInfoBean(classLoader,
                                                excludeUnlistedClasses == null ? true : excludeUnlistedClasses,
                                                jarFileUrls,
                                                jtaDataSourceProvider,
                                                classes,
                                                mappingFiles,
                                                nonJtaDataSourceProvider,
                                                persistenceUnit.getProvider(),
                                                name,
                                                rootUrl,
                                                "2.2",
                                                properties,
                                                sharedCacheMode,
                                                classLoader,
                                                transactionType,
                                                validationMode);
                                                
    }
    return returnValue;
  }
  
}
