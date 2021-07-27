package com.tgt.crm.token.webflux;

import com.tgt.crm.token.core.SalesforceConfig;
import io.netty.handler.logging.LogLevel;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Configuration
@AllArgsConstructor
public class AuthWebClient {

  private final SalesforceConfig salesforceConfig;

  @Bean
  @Qualifier("sfAuthWebClient")
  @ConditionalOnClass(AdvancedByteBufFormat.class)
  public WebClient sfAuthWebClientWiretap(final WebClient.Builder webClientBuilder) {
    return webClientBuilder
        .clientConnector(
            new ReactorClientHttpConnector(
                HttpClient.create()
                    .wiretap(
                        "com.tgt.crm.token.webflux.sfAuthWebClient",
                        LogLevel.TRACE,
                        AdvancedByteBufFormat.TEXTUAL)))
        .baseUrl(salesforceConfig.getHost())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .build();
  }

  @Bean
  @Qualifier("sfAuthWebClient")
  @ConditionalOnMissingClass("reactor.netty.transport.logging.AdvancedByteBufFormat")
  public WebClient sfAuthWebClient(final WebClient.Builder webClientBuilder) {
    return webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
        .baseUrl(salesforceConfig.getHost())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .build();
  }
}
