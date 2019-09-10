# feign-spring

[![Build Status](https://travis-ci.org/velo/feign-spring.svg?branch=master)](https://travis-ci.org/velo/feign-spring?branch=master) 
[![Coverage Status](https://coveralls.io/repos/github/velo/feign-spring/badge.svg?branch=master)](https://coveralls.io/github/velo/feign-spring?branch=master) 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.marvinformatics.feign/feign-spring/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.marvinformatics.feign/feign-spring/) 
[![Issues](https://img.shields.io/github/issues/velo/feign-spring.svg)](https://github.com/velo/feign-spring/issues) 
[![Forks](https://img.shields.io/github/forks/velo/feign-spring.svg)](https://github.com/velo/feign-spring/network) 
[![Stars](https://img.shields.io/github/stars/velo/feign-spring.svg)](https://github.com/velo/feign-spring/stargazers)

# Feign Spring
This module overrides OpenFeign/feign annotation processing to instead use standard ones supplied by the spring annotations specification.


## Currently Supported Annotation Processing
Feign only supports processing java interfaces (not abstract or concrete classes).

ISE is raised when any annotation's value is empty or null.  Ex. `Path("")` raises an ISE.

Here are a list of behaviors currently supported.
### Type Annotations
#### `@RequestMapping`
Appends the ```value``` to `Target.url()`.  Can have tokens corresponding to `@PathVariable` annotations.
The ```method``` sets the request method.
The ```produces``` adds the first value as the `Accept` header.
The ```consume``` adds the first value as the `Content-Type` header.
### Method Annotations
#### `@RequestMapping`
Appends the value to `Target.url()`.  Can have tokens corresponding to `@PathVariable` annotations.
The method sets the request method.
### Parameter Annotations
#### `@PathVariable`
Links the value of the corresponding parameter to a template variable declared in the path.
#### `@RequestParam`
Links the value of the corresponding parameter to a query parameter.  When invoked, null will skip the query param.