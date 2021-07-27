# Token Manager for Salesforce

This project makes Salesforce API calls with Spring a breeze. It exposes either a RestTemplate or WebClient that handles generating, refreshing and attaching an auth token header for every API call. Just pass in your instance and user credentials via `application.yml`, autowire your desired bean and it takes care of the rest.

## Usage

To use this library, the following repository declaration and one of the dependencies to your project's `build.gradle` file. This will include the core module as well automatically.

```groovy
repositories {
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/target/token-manager-for-salesforce")
    credentials {
      username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
      password = project.findProperty("gpr.token") ?: System.getenv("TOKEN")
    }
  }
}
dependencies {
  // for reactive applications
  implementation "com.tgt.crm:token-manager-for-salesforce-webflux:${libraryVersion}"
  // for non-reactive applications
  implementation "com.tgt.crm:token-manager-for-salesforce-webmvc:${libraryVersion}"
}
```

Find the latest version in the "packages" section of this repo. You will need access to the Github repository and will need to make your user and a personal access token available as a project or system property to authorize with Github and download the package. More details can be found [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry).

Add the following to your `application.yml` file to pass in Salesforce properties from TAP secrets/environment variables. You can set explicit values for these properties instead of environment variables if you prefer. Ensure you don't check any secrets into Git.

```yaml
salesforce:
  host: ${SALESFORCE_HOST}
  username: ${SALESFORCE_USERNAME}
  password: ${SALESFORCE_PASSWORD}
  client-id: ${SALESFORCE_CLIENT_ID}
  client-secret: ${SALESFORCE_CLIENT_SECRET}
  auth-uri: /services/oauth2/token # optional
  retry-backoff-delay: 1000 # optional, configures retry for auth token requests only
  max-auth-token-retries: 3 # optional, configures retry for auth token requests only
  retry-backoff-multiplier: 2 # optional, configures retry for auth token requests only, only used by MVC, see SalesforceConfig for more info
  httpclient:
    max-conn-per-route: 20 # optional
    read-timeout: 30000 # optional, in milliseconds
    connection-timeout: 60000 # optional, in milliseconds
    mvc: # configs in this block only available for webmvc
        max-pools: 50 # optional
        connection-request-timeout: 30000 # optional, in milliseconds
        retries: 3 # optional, MVC only, configures default # of retries for all requests except auth token
        retry-interval: 2000 # optional, in milliseconds, configures default retry interval for all requests except auth token
```

You should then be able to autowire the RestTemplate or WebClient bean in any component in your project and use it to make API calls to Salesforce.

```java
@Service
public class SalesforceClient {

  private final RestTemplate salesforceRestTemplate;

  public SalesforceClient(@Qualifier("sfRestTemplate") final RestTemplate salesForceRestTemplate) {
      this.salesForceRestTemplate = salesForceRestTemplate;
  }

  public String querySalesforce() {
    ResponseEntity<String> sfResponse =
        salesForceRestTemplate.exchange(
            "/services/data/v50.0/query&q={query}",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class,
            "SELECT Id FROM Case");

    return sfResponse.getBody();
  }
}
```

```java
@Service
public class SalesforceClient {

  private final WebClient salesforceWebClient;

  public SalesforceClient(@Qualifier("sfWebClient") final WebClient salesforceWebClient) {
      this.salesforceWebClient = salesforceWebClient;
  }

  public Mono<String> querySalesforce() {
    return webClient
        .get()
        .uri("/services/data/v50.0/query&q={query}", "SELECT Id FROM Case")
        .retrieve()
        .toEntity(String.class)
        .map(HttpEntity::getBody);
  }
}
```

### Minimum Requirements

In your project, the following minimum versions of Spring Boot are required to use this library:

* token-manager-for-salesforce-webflux: Spring Boot > 2.2.6.RELEASE
    * Spring Boot > 2.4.0 is recommended to support Wiretap for WebClient debug logging
* token-manager-for-salesforce-webmvc: Spring Boot > 2.2.0.RELEASE

### Metrics

The application also emits one micrometer metric. If a token refresh fails, a counter is incremented. The counter is called `exception_counter` and has one tag `exception_type` with value `token_refresh_exception`. This can be used to set up an alert in Grafana if a token refresh ever fails.

### How does it work?

This library follows the [OAuth 2.0 Username-Password Flow](https://help.salesforce.com/articleView?id=remoteaccess_oauth_username_password_flow.htm&type=5) and is intended to be used with first-party applications.

When your application starts up it will not have a token. The first time it makes an API call, the library intercepts the request, makes the appropriate API call to `/services/oauth2/token` for your configured instance as documented [here](https://help.salesforce.com/articleView?id=remoteaccess_oauth_endpoints.htm&type=5). The generated token is attached to the request as an `Authorization` header and the request proceeds. It is also cached in memory.

Subsequent requests use the cached token and do not try to request a new token unless a 401 response is received. When a 401 is received, it attempts to generate a new token and retries the request. We use this behavior because Salesforce auth tokens do not return an `expires_in` property and the length of time they are valid for can vary from instance to instance based on admin settings.

### Debugging Requests

It is possible and occasionally useful to log complete HTTP requests and responses including URLs, query params, headers and bodies. Be careful as this has the potential to expose sensitive data such as passwords, auth tokens or API keys. It is recommended to only use this when running the application locally.

To enable request/response logging for either mvc or webflux library, add the following to your `application.yml`:

```yaml
logging:
  level:
    com.tgt.crm.token: TRACE
    org.springframework.web.client.RestTemplate: DEBUG # additional RestTemplate debug logs, only applies to MVC. WARNING: logs sensitive info
    org.apache.http: DEBUG # additional detailed logging for RestTemplate, only applies to MVC. WARNING: logs sensitive info
    reactor.netty: TRACE # additional detailed logging for WebClient, only applies to WebFlux
```

Note that setting log level to `DEBUG` will enable all debug logging except request/response logging. This is by design to help prevent unintentional sensitive data exposure.

## Local Development

If you make changes to this library locally and want to test those changes with another application, you can use [Gradle Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html). Essentially you just tell your other application to point to this project on your local file system instead of downloading the dependency from a repository.

In the `settings.gradle` file in the root of the other project (you may have to create the file if it doesn't exist in that project), add the following line:

```
includeBuild '../token-manager-for-salesforce'
```

Where `../token-manager-for-salesforce` is the relative path to this project on your local file system. Now when you build the other project, it should use your local copy of `token-manager-for-salesforce`. Be sure to remove this change before committing.

## Publish a new version

Make the desired changes locally. Open a PR against the master branch, get it reviewed and merge. Tag this commit with a tag following [semantic versioning](https://semver.org/). This will trigger a new deployment of the library to [Github Packages](https://github.com/orgs/target/packages?repo_name=token-manager-for-salesforce).

## Troubleshooting

Problem: When I try to start my application I am getting a `NoClassDefFoundError` or a `NoSuchMethodError`.

Solution: Check the version of Spring Boot you are using in your app and that it meets the minimum requirement listed above in the README.

## License

token-manager-for-salesforce is licensed under the [MIT License](LICENSE.md)

Salesforce is a trademark of Salesforce.com, inc., and is used here with permission.
