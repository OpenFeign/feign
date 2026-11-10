# AGENTS.md

This file provides guidance to AI coding assistants when working with code in this repository.

## Build Commands

### Essential Build Commands
- `mvn clean install` - Default build command (installs all modules)
- `mvn clean install -Pdev` - **ALWAYS use this profile for development** - enables code formatting and other dev tools
- `mvn clean install -Pquickbuild` - Skip tests and validation for faster builds
- `mvn test` - Run all tests
- `mvn test -Dtest=ClassName` - Run specific test class

### Module-specific Commands
- `mvn clean install -pl core` - Build only the core module
- `mvn clean install -pl core,gson` - Build specific modules
- `mvn clean test -pl core -Dtest=FeignTest` - Run specific test in specific module

### Code Quality
- Code is automatically formatted using Google Java Format via git hooks
- License headers are enforced via maven-license-plugin
- Use `mvn validate` to check formatting and license compliance

## Project Architecture

### Core Architecture
Feign is a declarative HTTP client library with a modular design:

**Core Module (`core/`)**: Contains the main Feign API and implementation
- `Feign.java` - Main factory class for creating HTTP clients
- `Client.java` - HTTP client abstraction (default implementation + pluggable alternatives)
- `Contract.java` - Annotation processing interface (Default, JAX-RS, Spring contracts)
- `Encoder/Decoder.java` - Request/response serialization interfaces
- `Target.java` - Represents the remote HTTP service to invoke
- `RequestTemplate.java` - Template for building HTTP requests with parameter substitution
- `MethodMetadata.java` - Metadata about interface methods and their annotations

**Integration Modules**: Each module provides integration with specific libraries:
- `gson/`, `jackson/`, `fastjson2/` - JSON serialization
- `okhttp/`, `httpclient/`, `hc5/`, `java11/` - HTTP client implementations  
- `jaxrs/`, `jaxrs2/`, `jaxrs3/` - JAX-RS annotation support
- `spring/` - Spring MVC annotation support
- `hystrix/` - Circuit breaker integration
- `micrometer/`, `dropwizard-metrics4/5/` - Metrics integration
- `validation/`, `validation-jakarta/` - JSR-303 / Jakarta Bean Validation via the `MethodInterceptor` extension point
- `webflux/` - Non-blocking `AsyncClient` (`WebClientAsyncClient`) based on Spring `WebClient`
- `http-cache/` - Conditional revalidation (`ETag` / `Last-Modified` / `304 Not Modified`) via the `MethodInterceptor` extension point
- `jdk-json/` - Codec for the JDK Simple JSON API (`jdk.incubator.json`, JEP 540). It only builds if you use `-Pjdk28-modules` and a JDK 28 toolchain.

### Key Design Patterns
- **Builder Pattern**: `Feign.builder()` for configuring clients
- **Factory Pattern**: `Feign.newInstance(Target)` creates proxy instances
- **Strategy Pattern**: Pluggable `Client`, `Encoder`, `Decoder`, `Contract` implementations
- **Template Method**: `RequestTemplate` for building HTTP requests with parameter substitution
- **Proxy Pattern**: Dynamic proxies created for interface-based clients

### Multi-module Maven Structure
- Parent POM manages dependencies and common configuration
- Each integration is a separate Maven module
- Modules can be built independently: `mvn clean install -pl module-name`
- Example modules depend on `feign-core` and their respective 3rd party libraries

### Testing Strategy
- `feign-core` contains `AbstractClientTest` base class for testing HTTP clients
- Each module has its own test suite
- Integration tests use MockWebServer for HTTP mocking
- Tests are run with JUnit 5 and AssertJ assertions

## Development Notes

### Code Style
- Google Java Format is enforced via git hooks
- Code is formatted automatically on commit
- Package-private visibility is preferred over public when possible
- 3rd party dependencies are minimized in core module

### Module Dependencies
- Core module: Minimal dependencies (only what's needed for HTTP client abstraction)
- Integration modules: Add specific 3rd party libraries (Jackson, OkHttp, etc.)
- BOM (Bill of Materials) manages version consistency across modules

### Java Version Support
- Source/target: Java 8 (for `src/main`)
- Tests: Java 21 (for `src/test`)
- Maintains backwards compatibility with Java 8 in main codebase

### Updating Java Version for a Module
When a module's dependencies require a newer Java version (e.g., due to dependency upgrades), you need to override the Java version in that module's `pom.xml`:

1. Add a `<properties>` section to the module's `pom.xml` (or update existing one)
2. Set `<main.java.version>` to the required version (11, 17, 21, etc.)

Example:
```xml
<properties>
  <main.java.version>17</main.java.version>
</properties>
```

**Common scenarios requiring Java version updates:**
- Dropwizard Metrics 5.x requires Java 17
- Handlebars 4.5.0+ requires Java 17
- Jakarta EE modules typically require Java 11+

**Examples of modules with custom Java versions:**
- `spring/` - Java 17 (for Spring 6.x)
- `jaxrs4/` - Java 17 (for Jakarta EE 9+)
- `dropwizard-metrics5/` - Java 17 (for Metrics 5.x)
- `apt-test-generator/` - Java 17 (for Handlebars 4.5.0+)
- `soap-jakarta/`, `jaxb-jakarta/` - Java 11 (for Jakarta namespace)
- `jdk-json/` - This uses Java 28 (for `jdk.incubator.json`). It is in the `jdk28-modules` profile. This means builds without a JDK 28 toolchain still work. CI extracts the JDK 28 early-access build from the `openjdk:28-ea-jdk` image. This image is pinned to a specific digest in `.circleci/jdk28/Dockerfile`. Dependabot keeps this image up to date.

### Dependabot Configuration

Some modules define the same Maven property name (e.g., `jersey.version`, `vertx.version`) at different major versions. Dependabot treats these as a single property across the reactor and tries to set them all to the same value, which breaks modules locked to a specific major.

**Current split-property modules:**
- `jersey.version`: 2.x (jaxrs2), 3.x (jaxrs3), 4.x (jaxrs4)
- `vertx.version`: 4.x (feign-vertx4-test), 5.x (feign-vertx, feign-vertx5-test)

**How it works in `.github/dependabot.yml`:**
1. The root `/` entry **ignores** the conflicting dependencies entirely (jersey, vertx)
2. Each module gets its own entry with `allow` (only the conflicting dependency) and `ignore` (block major version bumps)
3. Other dependencies that use **different property names** per major (e.g., `jaxb-impl-2.version` vs `jaxb-impl-4.version`) only need `update-types: ["version-update:semver-major"]` on the root entry

**When adding a new module that reuses a version property at a different major:**
1. Add the dependency to the root entry's `ignore` list (fully ignored, not just major)
2. Add a per-directory entry for the new module with `allow` for the specific dependency and `ignore` for `version-update:semver-major`
3. Verify existing modules with the same property also have their own per-directory entries

## Consolidating master into 14.x

`master` is the binary-compatible 13.x line. `14.x` is the breaking v14 line (`14.0-alpha1-SNAPSHOT`) and must periodically absorb `master`. Merge, never rebase: `git merge origin/master` on an up-to-date `14.x`. `master` is always an ancestor after each merge, so only new master commits come in.

### What differs on 14.x

| Area | master (13.x) | 14.x |
|---|---|---|
| Module layout | everything in `core/` (`feign.*`) | public API in `api/` (`feign.*`); default implementations in `core/` (`feign.core.*`). Tests that build a Feign client live in `core/src/test` |
| Defaults | `Client.Default`, `Contract.Default`, `Retryer.Default`, `Encoder.Default`, `Decoder.Default`, `ErrorDecoder.Default` | `feign.core.DefaultClient`, `DefaultContract`, `DefaultRetryer`, `feign.core.codec.DefaultEncoder`, `DefaultDecoder`, `DefaultErrorDecoder` |
| Other moves | `feign.querymap.*`, `feign.codec.StringDecoder` | `feign.core.querymap.*`, `feign.core.codec.StringDecoder` |
| Request body | `byte[]` + `Charset` | streaming `Request.Body` interface, see `MIGRATION-v14.md` |
| `Request.create` | `(method, url, headers, byte[], Charset, template)` | `(method, url, headers, Request.Body, template)`; there is no `Request.charset()` or `Request.Body.empty()` (pass `null`) |
| Reading bodies | `request.body()` / `template.body()` return `byte[]` | `request.body()` / `template.requestBody()` return `Optional<Request.Body>`; read with `writeToByteArray()` / `writeToString(charset)` |
| Mock server | `okhttp3.mockwebserver` | `mockwebserver3` (`<artifactId>mockwebserver3</artifactId>`) |
| Main Java level | 8 | 11 (`final var` instead of lombok `val`, `Optional.isEmpty()` is fine) |

### mockwebserver → mockwebserver3

| master | 14.x |
|---|---|
| `new MockResponse().setBody(x).setResponseCode(c)` | `new MockResponse.Builder().body(x).code(c).build()` |
| `setChunkedBody`, `setBodyDelay`, `setHeadersDelay` | `chunkedBody`, `bodyDelay`, `headersDelay` |
| `setSocketPolicy(DISCONNECT_AFTER_REQUEST)` | `onResponseStart(new SocketEffect.CloseSocket())` |
| `server.useHttps(factory, false)` | `server.useHttps(factory)` |
| `server.shutdown()` | `server.close()`, and call `server.start()` explicitly |
| `recorded.getPath()` / `getHeader(h)` / `getSequenceNumber()` | `getTarget()` / `getHeaders().get(h)` / `getExchangeIndex()` |
| `recorded.getBody().readUtf8()` | `getBody().utf8()` |

### Merge recipe

1. Pom conflicts are almost always only the project version. Resolve them hunk by hunk and keep the `14.x` side. Never use `git checkout --ours` on whole poms, because that drops dependabot bumps that merged cleanly. `feign-bom/pom.xml` needs a union: keep new master module entries, but use the 14.x version.
2. New master modules arrive with master's parent version. After resolving, run `grep -rn '13\.[0-9]*-SNAPSHOT' --include=pom.xml .` and fix every hit.
3. For source conflicts, keep the 14.x structure (moved package, streaming body) and re-apply the master fix on top. Auto-merged master tests usually still use the 13.x APIs from the tables above. Port them.
4. Do not reintroduce `@Deprecated` API or 13.x body semantics. Drop master tests that only assert 13.x behaviour, such as charset on `Request` or binary-body logging.
5. If master deleted a file that 14.x edited (e.g. `CHANGELOG.md`), follow master.
6. Loop `mvn -o install -DskipTests -Dgcf.skip=true -Dlicense.skip=true -Djapicmp.skip=true` until it compiles, adding `-rf :<module>` each round. Then format with `mvn -Pdev initialize`, then run the full `mvn install`.
7. Modules behind JDK profiles (`jdk-json`, `-Pjdk28-modules`) are not compiled by the loop above. Check them separately.
8. Keep the parent `pom.xml` japicmp `<excludes>` for the v14 breaking types. Master does not have them.

## Releasing

The release script is at `scripts/release.sh`. It handles version updates, tagging, and pushing.

### Usage
- `./scripts/release.sh` — auto-detect release version from pom (strips `-SNAPSHOT`), auto-compute next snapshot
- `./scripts/release.sh <release-version>` — release a specific version, auto-compute next snapshot
- `./scripts/release.sh <release-version> <next-snapshot>` — release a specific version with explicit next snapshot

### Examples
```bash
# Standard release (pom is at 13.10-SNAPSHOT, releases 13.10, next becomes 13.11-SNAPSHOT)
./scripts/release.sh

# Patch release with custom next snapshot
./scripts/release.sh 13.9.1 13.10-SNAPSHOT
```

### What the script does
1. Sets pom versions to the release version (removes `-SNAPSHOT`)
2. Formats license headers and commits locally (no push)
3. Creates and pushes a git tag for the release version
4. Sets pom versions to the next snapshot and commits/pushes

### Patch releases
When doing a patch release (e.g., 13.9.1 while pom is at 13.10-SNAPSHOT), pass both arguments so the next snapshot returns to the current development version:
```bash
./scripts/release.sh 13.9.1 13.10-SNAPSHOT
```

## Documentation Requirements

- New modules must include a `README.md` with usage examples following the style of existing module READMEs (e.g., `jackson/README.md`, `graphql/README.md`)
- New public functionality (annotations, contracts, encoders, decoders) must be documented in the module's `README.md`
- README should include: Maven dependency coordinates, `Feign.builder()` configuration examples, and advanced usage if applicable
- Update this file's Integration Modules list when adding a new module
