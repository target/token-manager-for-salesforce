package com.tgt.crm.token.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class BaseIntegrationTest {

  @SuppressFBWarnings("MS_PKGPROTECT")
  protected static MockWebServer mockWebServer;

  protected static final int TIMEOUT = 5; // seconds
  protected static final String QUERY_SUCCESSFUL = "query successful";
  protected static final String SF_URL = "/some/sf/url";

  @BeforeAll
  static void setup() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterAll
  static void shutdown() throws IOException {
    mockWebServer.shutdown();
  }

  @DynamicPropertySource
  @SuppressWarnings("PMD.DefaultPackage")
  static void registerProperties(final DynamicPropertyRegistry registry) {
    registry.add("salesforce.host", () -> "http://localhost:" + mockWebServer.getPort());
  }

  protected void validateAuthRequest() throws InterruptedException {
    RecordedRequest authReq = mockWebServer.takeRequest(TIMEOUT, TimeUnit.SECONDS);
    assertNotNull(authReq);
    assertEquals(HttpMethod.POST.name(), authReq.getMethod());
    assertEquals("/services/oauth2/token", authReq.getPath());
    assertEquals(
        MediaType.APPLICATION_FORM_URLENCODED_VALUE, authReq.getHeader(HttpHeaders.CONTENT_TYPE));
    assertEquals(
        "grant_type=password&username=username&password=password%21%40%23%24%25%5E%26*%28%29&client_id=clientId&client_secret=clientSecret",
        authReq.getBody().readUtf8());
  }

  protected void validateSfRequest() throws InterruptedException {
    validateSfRequest("Bearer bearerToken");
  }

  protected void validateSfRequest(final String authHeader) throws InterruptedException {
    RecordedRequest queryReq = mockWebServer.takeRequest(TIMEOUT, TimeUnit.SECONDS);
    assertNotNull(queryReq);
    assertEquals(HttpMethod.GET.name(), queryReq.getMethod());
    assertEquals("/some/sf/url", queryReq.getPath());
    assertEquals(1, queryReq.getHeaders().values(HttpHeaders.CONTENT_TYPE).size());
    assertEquals(MediaType.APPLICATION_JSON_VALUE, queryReq.getHeader(HttpHeaders.CONTENT_TYPE));
    assertEquals(authHeader, queryReq.getHeader(HttpHeaders.AUTHORIZATION));
  }
}
