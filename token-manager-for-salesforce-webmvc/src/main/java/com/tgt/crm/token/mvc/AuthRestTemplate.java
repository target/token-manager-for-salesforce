package com.tgt.crm.token.mvc;

import com.tgt.crm.token.core.SalesforceConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@AllArgsConstructor
@Slf4j
@Configuration
public class AuthRestTemplate {

  private final SalesforceConfig salesforceConfig;

  @Bean
  public RestTemplate sfAuthRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
    return restTemplateBuilder
        .rootUri(salesforceConfig.getHost())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .build();
  }
}
