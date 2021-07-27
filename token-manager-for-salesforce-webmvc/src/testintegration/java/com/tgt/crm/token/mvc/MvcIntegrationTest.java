package com.tgt.crm.token.mvc;

import static com.tgt.crm.token.core.MockResponseUtil.getSfAuthErrorResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfAuthSuccessResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfQueryResponse;
import static com.tgt.crm.token.core.MockResponseUtil.getSfQueryUnauthorizedResponse;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tgt.crm.token.core.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = {
      SalesforceLibraryAutoConfiguration.class,
      RestTemplateAutoConfiguration.class,
      SimpleMeterRegistry.class
    })
@Slf4j
public class MvcIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private @Qualifier("sfRestTemplate") RestTemplate restTemplate;

  @Autowired private MeterRegistry meterRegistry;

  @BeforeEach
  public void setupEach() {
    meterRegistry.clear();
  }

  @Test
  void makeRequest_noTokenInCache_authSuccess_reqSuccess() throws InterruptedException {
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    ResponseEntity<String> res = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, res.getBody());

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount);

    validateAuthRequest();
    validateSfRequest();
  }

  @Test
  void makeRequest_tokenInCache_noAuthCall_reqSuccess() throws InterruptedException {
    // === make first request to cache token ===
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount1 = mockWebServer.getRequestCount();

    ResponseEntity<String> firstRes = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, firstRes.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, firstRes.getBody());

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount1);

    validateAuthRequest();
    validateSfRequest();

    // === make second request to test cached token is used ===
    mockWebServer.enqueue(getSfQueryResponse());
    int prevReqCount2 = mockWebServer.getRequestCount();

    ResponseEntity<String> secondRes = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, secondRes.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, secondRes.getBody());

    assertEquals(1, mockWebServer.getRequestCount() - prevReqCount2);
    validateSfRequest();
  }

  @Test
  void makeRequest_expiredTokenInCache_reqFails_tokenRefreshed_reqSuccess()
      throws InterruptedException {
    // === make first request to cache token ===
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount1 = mockWebServer.getRequestCount();

    ResponseEntity<String> firstRes = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, firstRes.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, firstRes.getBody());

    assertEquals(2, mockWebServer.getRequestCount() - prevReqCount1);

    validateAuthRequest();
    validateSfRequest();

    // === make second request to test token refresh after cached is unauthorized ===
    mockWebServer.enqueue(getSfQueryUnauthorizedResponse());
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount2 = mockWebServer.getRequestCount();

    ResponseEntity<String> secondRes = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, secondRes.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, secondRes.getBody());

    assertEquals(3, mockWebServer.getRequestCount() - prevReqCount2);

    validateSfRequest();
    validateAuthRequest();
    validateSfRequest();
  }

  @Test
  void makeRequest_noTokenInCache_authFail_retryAuthSuccess_reqSuccess()
      throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    ResponseEntity<String> res = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, res.getBody());

    assertEquals(3, mockWebServer.getRequestCount() - prevReqCount);

    validateAuthRequest();
    validateAuthRequest();
    validateSfRequest();
  }

  @Test
  void makeRequest_noTokenInCache_authFail_retryAuthFail_retryAuthSuccess_reqSuccess()
      throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthSuccessResponse());
    mockWebServer.enqueue(getSfQueryResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    ResponseEntity<String> res = restTemplate.getForEntity(SF_URL, String.class);

    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(QUERY_SUCCESSFUL, res.getBody());

    assertEquals(4, mockWebServer.getRequestCount() - prevReqCount);

    validateAuthRequest();
    validateAuthRequest();
    validateAuthRequest();
    validateSfRequest();
  }

  @Test
  void makeRequest_noTokenInCache_authFail_exhaustRetries_reqFail() throws InterruptedException {
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());
    mockWebServer.enqueue(getSfAuthErrorResponse());

    int prevReqCount = mockWebServer.getRequestCount();

    HttpClientErrorException ex =
        assertThrows(
            HttpClientErrorException.class, () -> restTemplate.getForEntity(SF_URL, String.class));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

    assertEquals(4, mockWebServer.getRequestCount() - prevReqCount);

    validateAuthRequest();
    validateAuthRequest();
    validateAuthRequest();

    // will still attempt the req but token will be null
    RecordedRequest reqWithNoAuth = mockWebServer.takeRequest(TIMEOUT, TimeUnit.SECONDS);
    assertNotNull(reqWithNoAuth);
    assertEquals(HttpMethod.GET.name(), reqWithNoAuth.getMethod());
    assertEquals("/some/sf/url", reqWithNoAuth.getPath());
    assertEquals(
        MediaType.APPLICATION_JSON_VALUE, reqWithNoAuth.getHeader(HttpHeaders.CONTENT_TYPE));
    assertEquals("", reqWithNoAuth.getHeader(HttpHeaders.AUTHORIZATION));

    assertEquals(
        1,
        meterRegistry
            .get(EXCEPTION_COUNTER)
            .tag(EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION)
            .counter()
            .count());
  }
}
