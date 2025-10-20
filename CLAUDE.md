# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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