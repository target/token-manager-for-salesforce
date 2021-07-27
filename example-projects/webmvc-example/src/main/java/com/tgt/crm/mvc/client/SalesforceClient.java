package com.tgt.crm.mvc.client;

import com.tgt.crm.mvc.vo.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class SalesforceClient {

  private static final String SF_BASE_URL = "/services/data/v51.0";
  private static final String SF_QUERY_URL = SF_BASE_URL + "/query?q={query}";

  private final RestTemplate salesforceRestTemplate;

  public SalesforceClient(@Qualifier("sfRestTemplate") final RestTemplate salesforceRestTemplate) {
    this.salesforceRestTemplate = salesforceRestTemplate;
  }

  public QueryResponse executeQuery(final String query) {

    log.info("executing query to Salesforce: {}", query);

    ResponseEntity<QueryResponse> sfResponse =
        salesforceRestTemplate.exchange(
            SF_QUERY_URL,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {},
            query);

    log.info("query response: {}", sfResponse);

    return sfResponse.getBody();
  }
}
