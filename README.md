# microBean JPA Weld SE Extension

The microBean JPA Weld SE Extension brings JPA to CDI.

If you are programming in an environment that already has JPA, such as
a Java EE application server, then this extension is not for you.

If you are writing a Java SE program using CDI, then this extension
may very well be for you.

In terms of end-user-observable effects, this extension allows you to
do the following:

```
public class MyCDIBean {

  @PersistenceContext(unitName = "dev")
  private EntityManager em;

}
```

That is, you can inject an `EntityManager` as though your CDI-based
standalone Java SE program were in fact a Java EE application server
(at least with respect to JPA).

This extension jumps through a few hoops to ensure that everything is
done as properly as possible.

Specifically, any `DataSource`s behind the scenes are sourced from CDI
itself as just regular CDI beans.  Additionally, if your CDI
implementation has transactional support, this extension will leverage
it.  Yes, that means your `persistence.xml` can specify transactional
data sources.

This extension is often used in conjunction with the [microBean
Narayana JTA CDI
Extension](https://github.com/microbean/microbean-narayana-jta-cdi)
project and its [Weld
variant](https://github.com/microbean/microbean-narayana-jta-weld-se)
and the [microBean DataSource CDI HikariCP
Extension](https://microbean.github.io/microbean-datasource-cdi-hikaricp/).

