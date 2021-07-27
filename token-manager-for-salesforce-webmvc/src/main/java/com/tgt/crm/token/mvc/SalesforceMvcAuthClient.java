package com.tgt.crm.token.mvc;

import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.MAX_AUTH_TOKEN_RETRIES_DEFAULT;
import static com.tgt.crm.token.core.SalesforceConstants.RETRY_BACKOFF_DELAY_DEFAULT;
import static com.tgt.crm.token.core.SalesforceConstants.RETRY_BACKOFF_MULTIPLIER_DEFAULT;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;

import com.tgt.crm.token.core.SalesforceAuthResponse;
import com.tgt.crm.token.core.SalesforceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class SalesforceMvcAuthClient {

  private final SalesforceConfig salesforceConfig;
  private final RestTemplate restTemplate;
  private final MeterRegistry meterRegistry;
  private String token;

  public SalesforceMvcAuthClient(
      final SalesforceConfig salesforceConfig,
      final @Qualifier("sfAuthRestTemplate") RestTemplate sfAuthRestTemplate,
      final MeterRegistry meterRegistry) {
    this.restTemplate = sfAuthRestTemplate;
    this.salesforceConfig = salesforceConfig;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Use to retrieve the cached token if there is one.
   *
   * @return the Salesforce oAuth bearer token
   */
  public String getToken() {
    return this.token == null ? null : this.token;
  }

  /**
   * Generates a new Salesforce token by calling Salesforce OAuth endpoint. Note that the @Retryable
   * annotation only works if this method is called from outside of this class. That is the reason
   * we return null from getToken() instead of calling refreshToken() there. This way we can check
   * if getToken() is null from wherever we call it and call generateToken() from outside of this
   * class if it is.
   */
  @Retryable(
      maxAttemptsExpression =
          "${salesforce.max-auth-token-retries:" + MAX_AUTH_TOKEN_RETRIES_DEFAULT + "}",
      backoff =
          @Backoff(
              multiplierExpression =
                  "${salesforce.retry-backoff-multiplier:" + RETRY_BACKOFF_MULTIPLIER_DEFAULT + "}",
              delayExpression =
                  "${salesforce.retry-backoff-delay:" + RETRY_BACKOFF_DELAY_DEFAULT + "}"))
  public String refreshToken() {
    log.debug("generateToken is called");

    ResponseEntity<SalesforceAuthResponse> salesforceAuthResponseEntity =
        restTemplate.exchange(
            salesforceConfig.getAuthUri(),
            HttpMethod.POST,
            new HttpEntity<>(initAuthString()),
            SalesforceAuthResponse.class);

    assert salesforceAuthResponseEntity.getBody() != null
        : "salesforce auth response body should never be null";
    this.token = salesforceAuthResponseEntity.getBody().getSalesforceAuthToken();
    log.info("token successfully generated");
    return this.token;
  }

  @Recover
  @SuppressWarnings("PMD.NullAssignment")
  public String handleRefreshFailure(final Throwable ex) {
    log.error("token refresh failed", ex);
    meterRegistry
        .counter(EXCEPTION_COUNTER, EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION)
        .increment();
    // set to null so the next time getToken is called, it will try to refresh token again
    this.token = null;
    return null;
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
