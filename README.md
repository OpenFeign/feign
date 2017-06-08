# Form Encoder

This module adds support for encoding **application/x-www-form-urlencoded** and **multipart/form-data** forms.

## Add dependency

Include the dependency to your project's pom.xml file:
```xml
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign.form</groupId>
        <artifactId>feign-form</artifactId>
        <version>2.2.0</version>
    </dependency>
    ...
</dependencies>
```

## Usage

Add `FormEncoder` to your `Feign.Builder` like so:

```java
SomeApi github = Feign.builder()
                     .encoder(new FormEncoder())
                     .target(SomeApi.class, "http://api.some.org");
```

Moreover, you can decorate the existing encoder, for example JsonEncoder like this:

```java
SomeApi github = Feign.builder()
                     .encoder(new FormEncoder(new JacksonEncoder()))
                     .target(SomeApi.class, "http://api.some.org");
```

And use them together:

```java
interface SomeApi {

    @RequestLine("POST /json")
    @Headers("Content-Type: application/json")
    void json (Dto dto);

    @RequestLine("POST /form")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    void from (@Param("field1") String field1, @Param("field2") String field2);

}
```

You can specify two types of encoding forms by `Content-Type` header.

### application/x-www-form-urlencoded

```java
interface SomeApi {

    ...

    @RequestLine("POST /authorization")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    void authorization (@Param("email") String email, @Param("password") String password);

    ...

}
```

### multipart/form-data

```java
interface SomeApi {

    ...

    @RequestLine("POST /send_photo")
    @Headers("Content-Type: multipart/form-data")
    void sendPhoto (@Param("is_public") Boolean isPublic, @Param("photo") File photo);

    ...

}
```

In example above, we send file in parameter named **photo** with additional field in form **is_public**.

> **IMPORTANT:** You can specify your files in API method by declaring type **File** or **byte[]**.

### Spring MultipartFile and Spring Cloud Netflix @FeignClient support

You can also use Form Encoder with Spring `MultipartFile` and `@FeignClient`.

Include the dependencies to your project's pom.xml file:
```xml
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign.form</groupId>
        <artifactId>feign-form</artifactId>
        <version>2.2.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.openfeign.form</groupId>
        <artifactId>feign-form-spring</artifactId>
        <version>2.2.0</version>
    </dependency>
    ...
</dependencies>
```

```java
@FeignClient(name = "file-upload-service", configuration = FileUploadServiceClient.MultipartSupportConfig.class)
public interface FileUploadServiceClient extends IFileUploadServiceClient {

    @Configuration
    public class MultipartSupportConfig {

        @Bean
        @Primary
        @Scope("prototype")
        public Encoder feignFormEncoder() {
            return new SpringFormEncoder();
        }
    }
}
```
