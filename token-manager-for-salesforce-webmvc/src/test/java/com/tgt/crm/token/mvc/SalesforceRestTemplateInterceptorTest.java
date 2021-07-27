package com.tgt.crm.token.mvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

@SuppressWarnings("ConstantConditions")
@ExtendWith(MockitoExtension.class)
public class SalesforceRestTemplateInterceptorTest {

  private static final byte[] TEST_BYTE_ARRAY = "body".getBytes(StandardCharsets.UTF_8);
  private static final String TEST_TOKEN_INVALID = "test_token_invalid";
  private static final String TEST_TOKEN = "test_token";

  @Mock private SalesforceMvcAuthClient authClient;
  @Mock private ClientHttpRequestExecution execution;
  @InjectMocks private SalesforceRestTemplateInterceptor tested;

  @Test
  public void nullToken_refreshToken_reqSuccess() throws IOException {
    MockClientHttpRequest request = new MockClientHttpRequest();
    ClientHttpResponse response = new MockClientHttpResponse(TEST_BYTE_ARRAY, HttpStatus.OK);

    when(authClient.getToken()).thenReturn(null);
    when(authClient.refreshToken()).thenReturn(TEST_TOKEN);
    when(execution.execute(request, TEST_BYTE_ARRAY)).thenReturn(response);

    ClientHttpResponse actualResponse = tested.intercept(request, TEST_BYTE_ARRAY, execution);

    assertEquals(response, actualResponse);

    ArgumentCaptor<HttpRequest> argument = ArgumentCaptor.forClass(HttpRequest.class);
    verify(execution).execute(argument.capture(), eq(TEST_BYTE_ARRAY));
    assertEquals(
        TEST_TOKEN, argument.getValue().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0));
    assertEquals(
        MediaType.APPLICATION_JSON_VALUE,
        argument.getValue().getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
  }

  @Test
  public void validToken_processRequest_noRefresh() throws IOException {
    MockClientHttpRequest request = new MockClientHttpRequest();
    ClientHttpResponse response = new MockClientHttpResponse(TEST_BYTE_ARRAY, HttpStatus.OK);

    when(authClient.getToken()).thenReturn(TEST_TOKEN);
    when(execution.execute(request, TEST_BYTE_ARRAY)).thenReturn(response);

    ClientHttpResponse actualResponse = tested.intercept(request, TEST_BYTE_ARRAY, execution);

    verify(authClient, never()).refreshToken();
    assertEquals(response, actualResponse);

    ArgumentCaptor<HttpRequest> argument = ArgumentCaptor.forClass(HttpRequest.class);
    verify(execution).execute(argument.capture(), eq(TEST_BYTE_ARRAY));
    assertEquals(
        TEST_TOKEN, argument.getValue().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0));
    assertEquals(
        MediaType.APPLICATION_JSON_VALUE,
        argument.getValue().getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
  }

  @Test
  public void invalidToken_reqFails_tokenRefreshed_reqSuccess() throws IOException {
    MockClientHttpRequest request = new MockClientHttpRequest();
    ClientHttpResponse expectedResponse =
        new MockClientHttpResponse(TEST_BYTE_ARRAY, HttpStatus.OK);
    ClientHttpResponse unauthorizedResponse =
        new MockClientHttpResponse(TEST_BYTE_ARRAY, HttpStatus.UNAUTHORIZED);

    when(authClient.getToken()).thenReturn(TEST_TOKEN_INVALID);
    when(authClient.refreshToken()).thenReturn(TEST_TOKEN);
    when(execution.execute(request, TEST_BYTE_ARRAY))
        .thenReturn(unauthorizedResponse)
        .thenReturn(expectedResponse);

    ClientHttpResponse actualResponse = tested.intercept(request, TEST_BYTE_ARRAY, execution);

    assertEquals(expectedResponse, actualResponse);

    ArgumentCaptor<HttpRequest> argument = ArgumentCaptor.forClass(HttpRequest.class);
    verify(execution, times(2)).execute(argument.capture(), eq(TEST_BYTE_ARRAY));

    assertEquals(
        TEST_TOKEN, argument.getValue().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0));
    assertEquals(
        MediaType.APPLICATION_JSON_VALUE,
        argument.getValue().getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
  }
}
