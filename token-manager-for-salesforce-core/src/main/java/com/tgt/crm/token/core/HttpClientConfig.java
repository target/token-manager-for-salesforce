package com.tgt.crm.token.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("salesforce.httpclient")
public class HttpClientConfig {

  private int maxConnPerRoute = 20;
  private int readTimeout = 30_000;
  private int connectionTimeout = 60_000;
}
