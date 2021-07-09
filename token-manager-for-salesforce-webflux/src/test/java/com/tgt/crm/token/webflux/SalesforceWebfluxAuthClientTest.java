package com.tgt.crm.token.webflux;

import static com.tgt.crm.token.core.SalesforceConstants.AUTH_URI;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgt.crm.token.core.SalesforceConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class SalesforceWebfluxAuthClientTest {

  private static final String BEARER_TOKEN = "Bearer bearerToken";
  private static final String TEST_USER_NAME = "testUserName";
  private static final String TEST_PASSWORD = "testPassword!@#$%^&*()";
  private static final String TEST_CLIENT_ID = "testClientId";
  private static final String TEST_CLIENT_SECRET = "testClientSecret";

  @Mock private SalesforceConfig salesforceConfig;
  @Mock private ExchangeFunction exchangeFunction;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  @Captor private ArgumentCaptor<ClientRequest> requestCaptor;

  private SalesforceWebfluxAuthClient systemUnderTest;

  private static String salesforceAuthResponse;
  private static String sfAuthResponseRefreshed;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    try {
      salesforceAuthResponse =
          MAPPER
              .readValue(
                  new ClassPathResource("salesforceAuthResponse.json").getInputStream(),
                  JsonNode.class)
              .toString();
      sfAuthResponseRefreshed =
          MAPPER
              .readValue(
                  new ClassPathResource("salesforceAuthResponseRefreshed.json").getInputStream(),
                  JsonNode.class)
              .toString();
    } catch (IOException e) {
      log.error("Error reading resource file", e);
    }
  }

  @BeforeEach
  public void setUp() {
    WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
    systemUnderTest = new SalesforceWebfluxAuthClient(webClient, salesforceConfig, meterRegistry);
  }

  @Test
  public void testGetToken_getTokenFirst_useCachedSecond() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);
    when(salesforceConfig.getAuthUri()).thenReturn(AUTH_URI);

    when(exchangeFunction.exchange(any(ClientRequest.class)))
        .thenReturn(buildMockResponseSuccess());

    Mono<String> actual = systemUnderTest.getToken();
    StepVerifier.create(actual).expectNextMatches(BEARER_TOKEN::equals).verifyComplete();

    // should execute callout first time
    verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));

    Mono<String> actualCached = systemUnderTest.getToken();
    StepVerifier.create(actualCached).expectNextMatches(BEARER_TOKEN::equals).verifyComplete();

    // verify exchange function was not called again, this means cached value was used
    verify(exchangeFunction, times(1)).exchange(requestCaptor.capture());
    ClientRequest actualRequest = requestCaptor.getValue();
    assertEquals(HttpMethod.POST, actualRequest.method());
    assertEquals(AUTH_URI, actualRequest.url().getPath());
    // not possible to read body from ClientRequest currently, verified in integration tests
  }

  @Test
  public void testGetToken_refreshToken() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);

    when(exchangeFunction.exchange(any(ClientRequest.class)))
        .thenReturn(buildMockResponseSuccess())
        .thenReturn(buildMockResponseRefreshed());

    Mono<String> actual = systemUnderTest.refreshToken();
    StepVerifier.create(actual).expectNextMatches(BEARER_TOKEN::equals).verifyComplete();

    // should execute callout first time
    verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));

    Mono<String> actualRefresh = systemUnderTest.refreshToken();
    StepVerifier.create(actualRefresh)
        .expectNextMatches("Bearer newBearerToken"::equals)
        .verifyComplete();

    // should refresh even if there is a cached value
    verify(exchangeFunction, times(2)).exchange(any(ClientRequest.class));
  }

  @Test
  public void testGetToken_fail_retries() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);

    when(exchangeFunction.exchange(any(ClientRequest.class)))
        .thenReturn(buildMockResponseFailure())
        .thenReturn(buildMockResponseSuccess());
    when(salesforceConfig.getMaxAuthTokenRetries()).thenReturn(3);
    when(salesforceConfig.getRetryBackoffDelay()).thenReturn(50);

    Mono<String> actual = systemUnderTest.refreshToken();
    StepVerifier.create(actual).expectNextMatches(BEARER_TOKEN::equals).verifyComplete();

    // should fail on first callout then succeed
    verify(exchangeFunction, times(2)).exchange(any(ClientRequest.class));
  }

  @Test
  public void testGetToken_fail_shouldNotUpdateToken() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);

    when(exchangeFunction.exchange(any(ClientRequest.class)))
        .thenReturn(buildMockResponseSuccess())
        .thenReturn(buildMockResponseFailure());
    when(meterRegistry.counter(EXCEPTION_COUNTER, EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION))
        .thenReturn(counter);
    when(salesforceConfig.getMaxAuthTokenRetries()).thenReturn(3);
    when(salesforceConfig.getRetryBackoffDelay()).thenReturn(50);

    Mono<String> actual = systemUnderTest.refreshToken();
    StepVerifier.create(actual).expectNextMatches(BEARER_TOKEN::equals).verifyComplete();
    // should succeed on first refresh
    verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));

    // on subsequent retry attempts it will fail
    // one attempt + 3 retries, throws RetryExhaustedException, child of RuntimeException
    Mono<String> actualRefresh = systemUnderTest.refreshToken();
    StepVerifier.create(actualRefresh)
        .expectError(RuntimeException.class)
        .verify(Duration.ofSeconds(10));

    // 1 for original refresh token, 1 for 2nd refresh token, 3 retires, 5 total calls
    verify(exchangeFunction, times(5)).exchange(any(ClientRequest.class));

    verify(counter).increment();
  }

  private Mono<ClientResponse> buildMockResponseSuccess() {
    return Mono.just(
        ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(salesforceAuthResponse)
            .build());
  }

  private Mono<ClientResponse> buildMockResponseRefreshed() {
    return Mono.just(
        ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(sfAuthResponseRefreshed)
            .build());
  }

  private Mono<ClientResponse> buildMockResponseFailure() {
    return Mono.just(
        ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build());
  }
}
