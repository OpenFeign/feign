# Feign OAuth2

This module extends **Feign** to enable client authentication using 
[OAuth2](https://datatracker.ietf.org/doc/html/rfc6749) 
and 
[OIDC](https://openid.net/specs/openid-connect-core-1_0.html) 
frameworks.

It automatically authenticates the client against an **OAuth2/OpenID Connect (OIDC) Authorization Server** using 
the `client_credentials` grant type. 
Additionally, it manages **access token renewal** seamlessly.

### Supported Authentication Methods

- ✅ `client_secret_basic` (OAuth2)
- ✅ `client_secret_post` (OAuth2)
- ✅ `client_secret_jwt` (OIDC)
- ✅ `private_key_jwt` (OIDC)

### 🛠️ Upcoming Features (Planned Support)

- 🚀 `tls_client_auth` (RFC 8705)
- 🚀 `self_signed_tls_client_auth` (RFC 8705)

### Compatibility

Designed to work with most **OAuth2/OpenID Connect** providers.  
Out-of-the-box support for:
- **AWS Cognito**
- **Okta Auth0**
- **Keycloak**

## Installation

### With Maven

```xml
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-oauth2</artifactId>
    </dependency>
    ...
</dependencies>
```

### With Gradle

```groovy
compile group: 'io.github.openfeign', name: 'feign-oauth2'
```

## Usage

Module provides `OAuth2Authentication` and `OpenIdAuthentication` generic capabilities, but also more specialized factory
classes: `AWSCognitoAuthentication`, `Auth0Authentication` and `KeycloakAuthentication`.

Here an example how to create an authenticated REST client by using **OIDC Discovery** of **Keycloak**:

```java
String issuer = String.format("http://keycloak:8080/realms/%s", "<keycloak realm>");

// Create an authentication
OpenIdAuthentication openIdAuthentication = OpenIdAuthentication.discover(ClientRegistration
        .builder()
        .credentials(Credentials
                .builder()
                .clientId("<client ID>")
                .clientSecret("<client secret>")
                .build())
        .providerDetails(ProviderDetails
                .builder()
                .issuerUri(issuer)
                .build())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .build());

IcecreamClient client = Feign
        .builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .addCapability(openIdAuthentication) // <-- add authentication to the Feign client
        .target(IcecreamClient.class, "http://localhost:5555");

// This call to the service will be authenticated
Collection<Mixin> mixins = client.getAvailableMixins();
```