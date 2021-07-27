package com.tgt.crm.token.mvc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("salesforce.httpclient.webmvc")
public class WebMvcHttpClientConfig {
  private int maxPools = 50;
  private int connectionRequestTimeout = 30_000;
  private int retries = 3;
  private int retryInterval = 2_000;
}
