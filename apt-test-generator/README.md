# Feign APT test generator
This module generates mock clients for tests based on feign interfaces

## Usage

Just need to add this module to dependency list and Java [Annotation Processing Tool](https://docs.oracle.com/javase/7/docs/technotes/guides/apt/GettingStarted.html) will automatically pick up the jar and generate test clients.

There are 2 main alternatives to include this to a project:

1. Just add to classpath and java compiler should automaticaly detect and run code generation. On maven this is done like this:

```xml
        <dependency>
            <groupId>io.github.openfeign.experimental</groupId>
            <artifactId>feign-apt-test-generator</artifactId>
            <version>${feign.version}</version>
            <scope>test</scope>
        </dependency>
```

1. Use a purpose build tool that allow to pick output location and don't mix dependencies onto classpath

```xml
            <plugin>
                <groupId>com.mysema.maven</groupId>
                <artifactId>apt-maven-plugin</artifactId>
                <version>1.1.3</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>process</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>target/generated-test-sources/feign</outputDirectory>
                            <processor>feign.apttestgenerator.GenerateTestStubAPT</processor>
                        </configuration>
                    </execution>
                </executions>
                <dependencies>
                    <dependency>
                        <groupId>io.github.openfeign.experimental</groupId>
                        <artifactId>feign-apt-test-generator</artifactId>
                        <version>${feign.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <id>feign-stubs-source</id>
                        <phase>generate-test-sources</phase>
                        <goals>
                            <goal>add-test-source</goal>
                        </goals>
                        <configuration>
                            <sources>
                                <source>target/generated-test-sources/feign</source>
                            </sources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```
