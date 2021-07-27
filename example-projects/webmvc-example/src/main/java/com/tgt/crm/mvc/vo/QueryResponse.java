package com.tgt.crm.mvc.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResponse {

  @JsonProperty("totalSize")
  private int totalSize;

  @JsonProperty("done")
  private boolean done;

  @JsonProperty("records")
  private List<Map<String, Object>> records;
}
