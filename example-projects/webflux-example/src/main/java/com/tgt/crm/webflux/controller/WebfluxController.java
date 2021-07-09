package com.tgt.crm.webflux.controller;

import com.tgt.crm.webflux.client.SalesforceClient;
import com.tgt.crm.webflux.vo.QueryResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
@RequestMapping("/salesforce")
@Slf4j
public class WebfluxController {

  private final SalesforceClient salesforceClient;

  @GetMapping("/query")
  public Mono<QueryResponse> querySalesforce(@RequestParam final String q) {

    return salesforceClient.executeQuery(q);
  }
}
