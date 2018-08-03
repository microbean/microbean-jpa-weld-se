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

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PersistenceUnit;

import javax.enterprise.context.Dependent;

// This annotation is a bean-defining annotation,
// but @Entity-annotated classes will be vetoed by the extension.
// This is just a cheap way to get this class discovered in an
// implicit (vs. explicit) bean archive.
@Dependent
@Entity
public class Person {

  @Id
  private long id;
  
  public Person() {
    super();
  }
  
}
