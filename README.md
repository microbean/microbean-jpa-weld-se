# microBean JPA (Weld SE)

[![Build Status](https://travis-ci.org/microbean/microbean-jpa-weld-se.svg?branch=master)](https://travis-ci.org/microbean/microbean-jpa-weld-se)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-jpa-weld-se/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.microbean/microbean-jpa-weld-se)

The microBean JPA (Weld SE) project brings JPA to standalone,
[Weld](http://weld.cdi-spec.org/)-based, CDI 2.0 programs.

**If you are programming in an environment that already has JPA, such
as a Java EE application server, then this project is not for you.**

If you are writing a Java SE program using CDI, then this project may
very well be for you.

This project works in conjunction with the [microBean JPA CDI
Extension](https://github.com/microbean/microbean-jpa-cdi/) CDI
portable extension.  The portable extension handles the truly portable
aspects of bringing JPA to CDI&mdash;and this project completes the
process with [an
implementation](https://microbean.github.io/microbean-jpa-weld-se/apidocs/org/microbean/jpa/weld/JpaInjectionServices.html)
of Weld's
[`JpaInjectionServices`](https://docs.jboss.org/weld/javadoc/2.4/weld-spi/org/jboss/weld/injection/spi/JpaInjectionServices.html)
service provider interface.  When the two of these projects are used
together, then in terms of end-user-observable effects you can do the
following:

```
public class MyCDIBean {

  @PersistenceContext(unitName = "dev")
  private EntityManager em;

}
```

That is, you can inject an `EntityManager` as though your Weld-based
standalone CDI 2.0 Java SE program were in fact a Java EE application
server (at least with respect to JPA).

This extension is often used in conjunction with the [microBean JPA
CDI Extension](https://github.com/microbean/microbean-jpa-cdi/)
project, the [microBean Narayana JTA CDI
Extension](https://github.com/microbean/microbean-narayana-jta-cdi)
project and its [Weld
variant](https://github.com/microbean/microbean-narayana-jta-weld-se)
and the [microBean DataSource CDI HikariCP
Extension](https://microbean.github.io/microbean-datasource-cdi-hikaricp/).

When you put all these components on your classpath, then any
`EntityManager` injected by being annotated with
[`@PersistenceContext`](https://javaee.github.io/javaee-spec/javadocs/javax/persistence/PersistenceContext.html)
that is involved in the execution of any method annotated with
[`@Transactional`](https://javaee.github.io/javaee-spec/javadocs/javax/transaction/Transactional.html)
will automatically join an automatically-created JTA `Transaction`,
managed by the [Narayana transaction engine](http://narayana.io/).
The result is EJB-like behavior without an EJB container or
application server.

If you have an implementation of [Bean
Validation](https://beanvalidation.org/) on your classpath, then it
will be incorporated into this project's overall JPA support as well.
