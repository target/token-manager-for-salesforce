package com.tgt.crm.token.mvc;

import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_COUNTER;
import static com.tgt.crm.token.core.SalesforceConstants.EXCEPTION_TYPE_TAG;
import static com.tgt.crm.token.core.SalesforceConstants.TOKEN_REFRESH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tgt.crm.token.core.SalesforceAuthResponse;
import com.tgt.crm.token.core.SalesforceConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.LinguisticNaming")
public class SalesforceMvcAuthClientTest {

  private static final String TEST_TOKEN_TYPE = "testTokenType";
  private static final String TEST_ACCESS_TOKEN = "testAccessToken";
  private static final String EXPECTED_TOKEN = TEST_TOKEN_TYPE + " " + TEST_ACCESS_TOKEN;
  private static final String TEST_USER_NAME = "testUserName";
  private static final String TEST_PASSWORD = "testPassword!@#$%^&*()";
  private static final String TEST_PASSWORD_ENCODED = "testPassword%21%40%23%24%25%5E%26*%28%29";
  private static final String TEST_CLIENT_ID = "testClientId";
  private static final String TEST_CLIENT_SECRET = "testClientSecret";
  private static final String REQUEST_ENTITY_BODY =
      "grant_type=password"
          + "&username="
          + TEST_USER_NAME
          + "&password="
          + TEST_PASSWORD_ENCODED
          + "&client_id="
          + TEST_CLIENT_ID
          + "&client_secret="
          + TEST_CLIENT_SECRET;
  private static final String AUTH_URI = "/services/oauth2/token";

  @InjectMocks private SalesforceMvcAuthClient tested;
  @Mock private RestTemplate restTemplate;
  @Mock private SalesforceConfig salesforceConfig;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private ResponseEntity<SalesforceAuthResponse> responseEntity;
  private HttpEntity<String> requestEntity;

  @BeforeEach
  public void setUpEach() {
    this.requestEntity = new HttpEntity<>(REQUEST_ENTITY_BODY);

    SalesforceAuthResponse validResponse = new SalesforceAuthResponse();
    validResponse.setTokenType(TEST_TOKEN_TYPE);
    validResponse.setAccessToken(TEST_ACCESS_TOKEN);
    this.responseEntity = new ResponseEntity<>(validResponse, HttpStatus.OK);
  }

  @Test
  public void getToken_noTokenInCache_nullResponse() {
    assertNull(tested.getToken());
  }

  @Test
  public void refreshToken_successfulResponse_tokenReturned() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);
    when(salesforceConfig.getAuthUri()).thenReturn(AUTH_URI);

    when(restTemplate.exchange(
            AUTH_URI, HttpMethod.POST, requestEntity, SalesforceAuthResponse.class))
        .thenReturn(responseEntity);

    assertEquals(EXPECTED_TOKEN, tested.refreshToken());

    verify(restTemplate)
        .exchange(AUTH_URI, HttpMethod.POST, requestEntity, SalesforceAuthResponse.class);
  }

  @Test
  public void getToken_tokenInCache_cachedTokenReturned() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);
    when(salesforceConfig.getAuthUri()).thenReturn(AUTH_URI);

    when(restTemplate.exchange(
            AUTH_URI, HttpMethod.POST, requestEntity, SalesforceAuthResponse.class))
        .thenReturn(responseEntity);

    tested.refreshToken();

    assertEquals(EXPECTED_TOKEN, tested.getToken());

    verify(restTemplate)
        .exchange(AUTH_URI, HttpMethod.POST, requestEntity, SalesforceAuthResponse.class);
  }

  @Test
  public void testHandleRefreshFailure() {
    when(salesforceConfig.getUsername()).thenReturn(TEST_USER_NAME);
    when(salesforceConfig.getPassword()).thenReturn(TEST_PASSWORD);
    when(salesforceConfig.getClientId()).thenReturn(TEST_CLIENT_ID);
    when(salesforceConfig.getClientSecret()).thenReturn(TEST_CLIENT_SECRET);
    when(salesforceConfig.getAuthUri()).thenReturn(AUTH_URI);
    when(meterRegistry.counter(EXCEPTION_COUNTER, EXCEPTION_TYPE_TAG, TOKEN_REFRESH_EXCEPTION))
        .thenReturn(counter);

    when(restTemplate.exchange(
            AUTH_URI, HttpMethod.POST, requestEntity, SalesforceAuthResponse.class))
        .thenReturn(responseEntity);

    assertEquals(EXPECTED_TOKEN, tested.refreshToken());

    assertNull(tested.handleRefreshFailure(new RuntimeException("test exception")));

    assertNull(tested.getToken());

    verify(counter).increment();
  }
}
