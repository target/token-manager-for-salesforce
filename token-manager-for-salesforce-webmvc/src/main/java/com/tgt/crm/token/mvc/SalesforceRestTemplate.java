package com.tgt.crm.token.mvc;

import com.tgt.crm.token.core.HttpClientConfig;
import com.tgt.crm.token.core.SalesforceConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@AllArgsConstructor
@Configuration
@Slf4j
public class SalesforceRestTemplate {

  private final SalesforceRestTemplateInterceptor sfRestTemplateInterceptor;
  private final SalesforceConfig salesforceConfig;
  private final HttpClientConfig httpClientConfig;
  private final WebMvcHttpClientConfig webMvcHttpClientConfig;

  @Bean
  public RestTemplate sfRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
    return restTemplateBuilder
        .requestFactory(this::getHttpFactory)
        .rootUri(salesforceConfig.getHost())
        .additionalInterceptors(sfRestTemplateInterceptor)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  private ClientHttpRequestFactory getHttpFactory() {
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(webMvcHttpClientConfig.getMaxPools());
    connectionManager.setDefaultMaxPerRoute(httpClientConfig.getMaxConnPerRoute());
    HttpClient httpClient =
        HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setRetryHandler(
                (exception, executionCount, context) -> {
                  log.error(
                      "{} exception thrown during execution count {} with message {}",
                      exception.getClass(),
                      executionCount,
                      exception.getMessage());
                  return executionCount < webMvcHttpClientConfig.getRetries();
                })
            .setServiceUnavailableRetryStrategy(
                new ServiceUnavailableRetryStrategy() {
                  @Override
                  public boolean retryRequest(
                      final HttpResponse response,
                      final int executionCount,
                      final HttpContext context) {
                    return HttpStatus.valueOf(response.getStatusLine().getStatusCode())
                            .is5xxServerError()
                        && executionCount < webMvcHttpClientConfig.getRetries();
                  }

                  @Override
                  public long getRetryInterval() {
                    return webMvcHttpClientConfig.getRetryInterval();
                  }
                })
            .build();
    HttpComponentsClientHttpRequestFactory httpFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    httpFactory.setReadTimeout(httpClientConfig.getReadTimeout());
    httpFactory.setConnectionRequestTimeout(webMvcHttpClientConfig.getConnectionRequestTimeout());
    httpFactory.setConnectTimeout(httpClientConfig.getConnectionTimeout());
    return httpFactory;
  }
}
