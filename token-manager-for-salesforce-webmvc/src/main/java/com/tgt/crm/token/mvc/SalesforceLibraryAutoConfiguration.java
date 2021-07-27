package com.tgt.crm.token.mvc;

import com.tgt.crm.token.core.HttpClientConfig;
import com.tgt.crm.token.core.SalesforceConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableConfigurationProperties({
  SalesforceConfig.class,
  HttpClientConfig.class,
  WebMvcHttpClientConfig.class
})
@EnableRetry
@ComponentScan
public class SalesforceLibraryAutoConfiguration {}
