package com.tgt.crm.token.webflux;

import com.tgt.crm.token.core.HttpClientConfig;
import com.tgt.crm.token.core.SalesforceConfig;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Configuration
@AllArgsConstructor
@Slf4j
public class SalesforceWebClient {

  private final SalesforceWebfluxAuthClient salesforceWebfluxAuthClient;
  private final SalesforceConfig salesforceConfig;
  private final HttpClientConfig httpClientConfig;

  @Bean
  @Qualifier("sfWebClient")
  @ConditionalOnClass(AdvancedByteBufFormat.class)
  public WebClient sfWebClientWiretap(final WebClient.Builder webClientBuilder) {
    HttpClient httpClient =
        HttpClient.create(
                ConnectionProvider.create(
                    "sfTokenManagerProvider", httpClientConfig.getMaxConnPerRoute()))
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(
                        new ReadTimeoutHandler(
                            httpClientConfig.getReadTimeout(), TimeUnit.MILLISECONDS)))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, httpClientConfig.getConnectionTimeout())
            .wiretap(
                "com.tgt.crm.token.webflux.sfWebClient",
                LogLevel.TRACE,
                AdvancedByteBufFormat.TEXTUAL);
    return webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(salesforceConfig.getHost())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .filter(addAuthHeader())
        .filter(retryOnUnauthorized())
        .build();
  }

  /** Uses deprecated configuration to support older versions of spring boot */
  @Bean
  @Qualifier("sfWebClient")
  @ConditionalOnMissingClass("reactor.netty.transport.logging.AdvancedByteBufFormat")
  public WebClient sfWebClient(final WebClient.Builder webClientBuilder) {
    HttpClient httpClient =
        HttpClient.create(
                ConnectionProvider.create(
                    "sfTokenManagerProvider", httpClientConfig.getMaxConnPerRoute()))
            .tcpConfiguration(
                tcpClient ->
                    tcpClient
                        .option(
                            ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            httpClientConfig.getConnectionTimeout())
                        .doOnConnected(
                            conn ->
                                conn.addHandlerLast(
                                    new ReadTimeoutHandler(
                                        httpClientConfig.getReadTimeout(),
                                        TimeUnit.MILLISECONDS))));
    return webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(salesforceConfig.getHost())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .filter(addAuthHeader())
        .filter(retryOnUnauthorized())
        .build();
  }

  private ExchangeFilterFunction addAuthHeader() {
    return (request, next) ->
        salesforceWebfluxAuthClient
            .getToken()
            .map(
                token ->
                    ClientRequest.from(request).header(HttpHeaders.AUTHORIZATION, token).build())
            .flatMap(next::exchange);
  }

  private ExchangeFilterFunction retryOnUnauthorized() {
    return (request, next) ->
        next.exchange(request)
            .flatMap(
                (Function<ClientResponse, Mono<ClientResponse>>)
                    clientResponse -> {
                      if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                        log.info("received 401 response, refreshing token and retrying request");
                        return salesforceWebfluxAuthClient
                            .refreshToken()
                            .map(
                                token ->
                                    ClientRequest.from(request)
                                        .headers(
                                            headers ->
                                                headers.replace(
                                                    HttpHeaders.AUTHORIZATION,
                                                    Collections.singletonList(token)))
                                        .build())
                            .flatMap(next::exchange);
                      } else {
                        return Mono.just(clientResponse);
                      }
                    });
  }
}
