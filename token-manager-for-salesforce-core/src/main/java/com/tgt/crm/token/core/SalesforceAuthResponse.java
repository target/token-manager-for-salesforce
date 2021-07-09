package com.tgt.crm.token.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesforceAuthResponse {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("instance_url")
  private String instanceUrl;

  private String id;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("issued_at")
  private String issuedAt;

  private String signature;

  private String error;

  @JsonProperty("error_description")
  private String errorDescription;

  public String getSalesforceAuthToken() {
    return tokenType + " " + accessToken;
  }
}
