package com.tgt.crm.webflux.client;

import com.tgt.crm.webflux.vo.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SalesforceClient {

  private static final String SF_BASE_URL = "/services/data/v51.0";
  private static final String SF_QUERY_URL = SF_BASE_URL + "/query?q={query}";

  private final WebClient salesforceWebClient;

  public SalesforceClient(@Qualifier("sfWebClient") final WebClient salesforceWebClient) {
    this.salesforceWebClient = salesforceWebClient;
  }

  public Mono<QueryResponse> executeQuery(final String query) {

    log.info("executing query to Salesforce: {}", query);

    return salesforceWebClient
        .get()
        .uri(SF_QUERY_URL, query)
        .retrieve()
        .bodyToMono(QueryResponse.class)
        .doOnError(
            err -> err instanceof WebClientResponseException,
            err ->
                log.error(
                    "error message: {}",
                    ((WebClientResponseException) err).getResponseBodyAsString()))
        .doOnSuccess(response -> log.info("query response: {}", response));
  }
}
