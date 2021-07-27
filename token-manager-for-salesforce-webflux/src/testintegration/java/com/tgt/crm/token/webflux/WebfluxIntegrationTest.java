package com.tgt.crm.token.webflux;

import static com.tgt.crm.token.core.MockResponseUtil.getSfAuthErrorResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfAuthRefreshedSuccessResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfAuthSuccessResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfQueryResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfQueryUnauthorizedResponse;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tgt.crm.token.core.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(
    classes = {
      SalesforceLibraryAutoConfiguration.class,
      WebClientAutoConfiguration.class,
      SimpleMeterRegistry.class
    })
@Slf4j
public class WebfluxIntegrationTest extends BaseIntegrationTest {

  @Qualifier("sfWebClient")
  @Autowired
  private WebClient webClient;

  @Autowired private MeterRegistry meterRegistry;

  @BeforeEach
  public void setupEach() {
    meterRegistry.clear();
  }

  @Test
  public void makeRequest_noTokenInCache_authSuccess_reqSuccess() throws InterruptedException {
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    validateAuthRequest();
    validateSfRequest();

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount);
  }

  @Test
  void makeRequest_tokenInCache_noAuthCall_reqSuccess() throws InterruptedException {
    // === make first request to cache token ===
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount1 = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual1 =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual1)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    validateAuthRequest();
    validateSfRequest();

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount1);

    // === make second request to test cached token is used ===
    mockWebServer.enqueue(getSfQueryResponse());
    int prevReqCount2 = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual2 =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual2)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    validateSfRequest();
    assertEquals(1, mockWebServer.getRequestCount() - prevReqCount2);
  }

  @Test
  void makeRequest_noTokenInCache_authFail_retryAuthSuccess_reqSuccess()
      throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    validateAuthRequest();
    validateAuthRequest();
    validateSfRequest();

    assertEquals(3, mockWebServer.getRequestCount() - prevReqCount);
  }

  @Test
  void makeRequest_noTokenInCache_authFail_retryAuthFail_retryAuthSuccess_reqSuccess()
      throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    validateAuthRequest();
    validateAuthRequest();
    validateAuthRequest();
    validateSfRequest();

    assertEquals(4, mockWebServer.getRequestCount() - prevReqCount);
  }

  @Test
  void makeRequest_noTokenInCache_authFail_exhaustRetries_reqFail() throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual)
        .expectError(RuntimeException.class)
        .verify(Duration.ofSeconds(TIMEOUT * 3));

    validateAuthRequest();
    validateAuthRequest();
    validateAuthRequest();
    validateAuthRequest();

    assertEquals(4, mockWebServer.getRequestCount() - prevReqCount);

    assertEquals(
        1,
        meterRegistry
            .get(EXCEPTION_COUNTER)
            .tag(EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION)
            .counter()
            .count());
  }

  @Test
  void makeRequest_expiredTokenInCache_reqFails_tokenRefreshed_reqSuccess()
      throws InterruptedException {
    // === make first request to cache token ===
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount1 = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual1 =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.setDefaultTimeout(Duration.ofSeconds(TIMEOUT));
    StepVerifier.create(actual1)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount1);

    validateAuthRequest();
    validateSfRequest();

    // === make second request to test token refresh after cached is unauthorized ===
    mockWebServer.enqueue(getSfQueryUnauthorizedResponse());
    mockWebServer.enqueue(getSfAuthRefreshedSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount2 = mockWebServer.getRequestCount();

    Mono<ResponseEntity<String>> actual2 =
        webClient.get().uri(SF_URL).retrieve().toEntity(new ParameterizedTypeReference<>() {});

    StepVerifier.create(actual2)
        .assertNext(
            nxt -> {
              assertEquals(HttpStatus.OK, nxt.getStatusCode());
              assertEquals(QUERY_SUCCESSFUL, nxt.getBody());
            })
        .verifyComplete();

    assertEquals(3, mockWebServer.getRequestCount() - prevReqCount2);

    validateSfRequest();
    validateAuthRequest();
    validateSfRequest("Bearer new bearerToken");
  }
}
