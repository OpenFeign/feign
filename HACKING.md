# Hacking Feign
Feign is optimized for maintenance vs flexibility. It prefers small
features that have been asked for repeated times, that are insured with
tests, and have clear use cases. This limits the lines of code and count
of modules in Feign's repo.

Code design is opinionated including below:

* Classes and methods default to package, not public visibility.
* Changing certain implementation classes may be unsupported.
* 3rd-party dependencies, and gnarly apis like java.beans are avoided.

## Building from source

Run the Maven wrapper from the repository root with `JAVA_HOME` pointing to JDK 25.
The JVM options in `.mvn/jvm.config` require a recent launcher JDK. Maven selects
separate toolchains to compile main code and run tests.

Main compilation follows each module's `main.java.version`, which defaults to
`1.8` and is overridden by Java 11 and Java 17 modules. Tests use the root POM's
`latest.java.version`, currently `25`. The default reactor uses these keys:

| Version key | Used for |
| --- | --- |
| `1.8` | Main code in core and other Java 8 modules |
| `11` | Main code in Java 11 modules |
| `17` | Main code in Java 17 modules |
| `25` | Test compilation and execution |

Use [.circleci/toolchains.xml](.circleci/toolchains.xml) as a reference. Merge the
required entries into `~/.m2/toolchains.xml`, replacing each `jdkHome` with your
installation path. For a macOS `.jdk` bundle, use its `Contents/Home` directory.
Use `11` for JDK 11, not `1.11`.

The version key describes the binary at `jdkHome`; Maven can select an incorrect
binary if its declared version is wrong. Check each installation with
`"/path/to/jdkHome/bin/java" -version` and `"/path/to/jdkHome/bin/javac" -version`.
The CI template also contains JDK 21 and 28 entries. JDK 28 is required when
selecting the optional `jdk28-modules` profile.

Build and test all standard modules with the development profile:

```shell
./mvnw -Pdev clean install
```

For a focused core build, include its reactor dependencies with `-am`:

```shell
./mvnw -Pdev -pl core -am clean install
```

To use a separate toolchains file, pass it explicitly:

```shell
./mvnw --toolchains /path/to/toolchains.xml -Pdev -pl core -am clean install
```

If Maven fails at startup with `Unrecognized option: --sun-misc-unsafe-memory-access=allow`,
check `"$JAVA_HOME/bin/java" -version` and select JDK 25 for the launcher. If it
reports a missing toolchain, check the requested version key and the corresponding
`jdkHome`. Changing Feign's Java version properties is not needed for local setup.
See [.circleci/config.yml](.circleci/config.yml) for CI's JDK setup and verification
with the optional profile.

## How to request change
The best way to approach something not yet supported is to ask on
[gitter](https://gitter.im/OpenFeign/feign) or [raise an issue](https://github.com/OpenFeign/feign/issues).
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
