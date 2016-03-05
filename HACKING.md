# Hacking Feign
Feign is optimized for maintenance vs flexibility. It prefers small
features that have been asked for repeated times, that are insured with
tests, and have clear use cases. This limits the lines of code and count
of modules in Feign's repo.

Code design is opinionated including below:

* Classes and methods default to package, not public visibility.
* Changing certain implementation classes may be unsupported.
* 3rd-party dependencies, and gnarly apis like java.beans are avoided.

## How to request change
The best way to approach something not yet supported is to ask on
[gitter](https://gitter.im/Netflix/feign) or [raise an issue](https://github.com/Netflix/feign/issues).
Asking for the feature you need (like how to deal with command groups)
vs a specific implementation (like making a private type public) will
give you more options to accomplish your goal.

Advice usually comes in two parts: advice and workaround. Advice may be 
to change Feign's code, or to fork until the feature is more widely
requested.

## How change works
High quality pull requests that have clear scope and tests that reflect
the intent of the feature are often merged and released in days. If a
merged change isn't immediately released and it is of priority to you,
nag (make a comment) on your merged pull request until it is released.

## How to experiment
Changes to Feign's code are best addressed by the feature requestor in a
pull request *after* discussing in an issue or on gitter. By discussing
first, there's less chance of a mutually disappointing experience where
a pull request is rejected. Moreover, the feature may be already present!

Albeit rare, some features will be deferred or rejected for inclusion in
Feign's main repository. In these cases, the choices are typically to
either fork the repository, or make your own repository containing the
change.

### Forks are welcome!
Forking isn't bad. It is a natural place to experiment and vet a feature
before it ends up in Feign's main repository. Large features or those
which haven't satisfied diverse need are often deferred to forks or
separate repositories (see [Rule of Three](http://blog.codinghorror.com/rule-of-three/)).

### Large integrations -> separate repositories
If you look carefully, you'll notice Feign integrations are often less
than 1000 lines of code including tests. Some features are rejected for
inclusion solely due to the amount of maintenance. For example, adding
some features might imply tying up maintainers for several days or weeks
and resulting in a large percentage increase in the size of feign.

Large integrations aren't bad, but to be sustainable, they need to be
isolated where the maintenance of that feature doesn't endanger the
maintainability of Feign itself. Feign has been going since 2012, without
the need of full-time attention. This is largely because maintenance is
low and approachable.

A good example of a large integration is [spring-cloud-netflix](https://github.com/spring-cloud/spring-cloud-netflix/tree/master/spring-cloud-netflix-core/src/main/java/org/springframework/cloud/netflix/feign).
Spring Cloud Netflix is sustainable as it has had several people
maintaining it, including Q&A support for years.
