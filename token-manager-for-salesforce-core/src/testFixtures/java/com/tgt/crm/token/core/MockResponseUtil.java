package com.tgt.crm.token.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Slf4j
public final class MockResponseUtil {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String sfAuthSuccess;
  private static String sfAuthError;
  private static String sfQueryUnauthorizedResponse;
  private static String sfAuthRefreshed;

  private MockResponseUtil() {}

  static {
    try {
      sfAuthSuccess = readFile("sfAuthSuccess.json");
      sfAuthError = readFile("sfAuthError.json");
      sfQueryUnauthorizedResponse = readFile("sfQueryUnauthorizedResponse.json");
      sfAuthRefreshed = readFile("sfAuthRefreshed.json");
    } catch (IOException e) {
      log.error("Error reading resource file", e);
      assert false;
    }
  }

  public static MockResponse getSfAuthSuccessResponse() {
    return buildSuccessResponse(sfAuthSuccess);
  }

  public static MockResponse getSfAuthRefreshedSuccessResponse() {
    return buildSuccessResponse(sfAuthRefreshed);
  }

  public static MockResponse getSfAuthErrorResponse() {
    return new MockResponse()
        .setResponseCode(400)
        .setBody(sfAuthError)
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
  }

  public static MockResponse getSfQueryResponse() {
    return buildSuccessResponse("query successful");
  }

  public static MockResponse getSfQueryUnauthorizedResponse() {
    return new MockResponse()
        .setResponseCode(401)
        .setBody(sfQueryUnauthorizedResponse)
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
  }

  private static MockResponse buildSuccessResponse(final String body) {
    return new MockResponse()
        .setResponseCode(200)
        .setBody(body)
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
  }

  private static String readFile(final String fileName) throws IOException {
    return MAPPER
        .readValue(new ClassPathResource(fileName).getInputStream(), JsonNode.class)
        .toString();
  }
}
