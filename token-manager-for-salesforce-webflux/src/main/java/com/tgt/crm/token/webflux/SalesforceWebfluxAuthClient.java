package com.tgt.crm.token.webflux;

import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;

import com.tgt.crm.token.core.SalesforceAuthResponse;
import com.tgt.crm.token.core.SalesforceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Configuration
public class SalesforceWebfluxAuthClient {

  private final WebClient authWebClient;
  private final SalesforceConfig salesforceConfig;
  private final MeterRegistry meterRegistry;
  private String token;

  public SalesforceWebfluxAuthClient(
      final @Qualifier("sfAuthWebClient") WebClient authWebClient,
      final SalesforceConfig salesforceConfig,
      final MeterRegistry meterRegistry) {
    this.authWebClient = authWebClient;
    this.salesforceConfig = salesforceConfig;
    this.meterRegistry = meterRegistry;
  }

  public Mono<String> getToken() {
    // will be null on first call to Salesforce
    if (this.token == null) {
      log.info("token is null, calling refresh token to generate first token");
      return refreshToken()
          .doOnError(error -> log.error("Error generating token. ", error))
          .doOnSuccess(success -> log.info("token generated successfully"));
    }
    return Mono.just(this.token);
  }

  public Mono<String> refreshToken() {
    return authWebClient
        .post()
        .uri(salesforceConfig.getAuthUri())
        .body(Mono.just(initAuthString()), String.class)
        .retrieve()
        .bodyToMono(SalesforceAuthResponse.class)
        .retryWhen(
            Retry.backoff(
                    salesforceConfig.getMaxAuthTokenRetries(),
                    Duration.ofMillis(salesforceConfig.getRetryBackoffDelay()))
                .doAfterRetry(retry -> log.error("Retry failed. ", retry.failure())))
        .doOnError(
            error -> {
              log.error("token refresh failed");
              meterRegistry
                  .counter(EXCEPTION_COUNTER, EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION)
                  .increment();
            })
        .doOnSuccess(success -> log.info("token refresh successful"))
        .map(val -> this.token = val.getSalesforceAuthToken());
  }

  private String initAuthString() {
    return "grant_type=password"
        + "&username="
        + salesforceConfig.getUsername()
        + "&password="
        + URLEncoder.encode(salesforceConfig.getPassword(), StandardCharsets.UTF_8)
        + "&client_id="
        + salesforceConfig.getClientId()
        + "&client_secret="
        + salesforceConfig.getClientSecret();
  }
}
