package com.tgt.crm.token.mvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

@AllArgsConstructor
@Slf4j
@Configuration
public class SalesforceRestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final SalesforceMvcAuthClient salesForceMvcAuthClient;

  @Override
  @NonNull
  public ClientHttpResponse intercept(
      final HttpRequest request,
      @NonNull final byte[] body,
      final ClientHttpRequestExecution execution)
      throws IOException {
    log.debug("Entering intercept method for Salesforce call");

    String token =
        salesForceMvcAuthClient.getToken() == null
            ? salesForceMvcAuthClient.refreshToken()
            : salesForceMvcAuthClient.getToken();

    request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
    request.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    logRequest(request, body);

    ClientHttpResponse response = execution.execute(request, body);

    logResponse(response);

    // if we get a 401, refresh the token and try request again
    if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
      log.info("received 401 response, refreshing token");
      response.close();
      request.getHeaders().set(HttpHeaders.AUTHORIZATION, salesForceMvcAuthClient.refreshToken());
      response = execution.execute(request, body);
      logResponse(response);
    }

    return response;
  }

  private void logRequest(final HttpRequest request, final byte[] body) {
    if (log.isTraceEnabled()) {
      HttpHeaders headerDeepCopy = SerializationUtils.clone(request.getHeaders());
      headerDeepCopy.setBearerAuth("************");
      log.trace(
          "===========================request begin=============================================");
      log.trace("URI         : {}", request.getURI());
      log.trace("Method      : {}", request.getMethod());
      log.trace("Headers     : {}", headerDeepCopy);
      log.trace("Request body: {}", new String(body, StandardCharsets.UTF_8));
      log.trace(
          "==========================request end===============================================");
    }
  }

  private void logResponse(final ClientHttpResponse response) throws IOException {
    if (log.isTraceEnabled()) {
      HttpHeaders headerDeepCopy = SerializationUtils.clone(response.getHeaders());
      headerDeepCopy.setBearerAuth("************");
      log.trace(
          "============================response begin==========================================");
      log.trace("Status code  : {}", response.getStatusCode());
      log.trace("Status text  : {}", response.getStatusText());
      log.trace("Headers      : {}", headerDeepCopy);
      log.trace(
          "=======================response end=================================================");
    }
  }
}
