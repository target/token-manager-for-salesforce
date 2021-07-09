package com.tgt.crm.mvc.controller;

import com.tgt.crm.mvc.client.SalesforceClient;
import com.tgt.crm.mvc.vo.QueryResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/salesforce")
@Slf4j
public class MvcController {

  private final SalesforceClient salesforceClient;

  @GetMapping("/query")
  public ResponseEntity<QueryResponse> querySalesforce(@RequestParam final String q) {

    QueryResponse response = salesforceClient.executeQuery(q);

    return ResponseEntity.ok(response);
  }
}
