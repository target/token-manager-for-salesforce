package com.tgt.crm.token.core;

import static com.tgt.crm.token.core.SalesforceConstants.AUTH_URI;
import static com.tgt.crm.token.core.SalesforceConstants.MAX_AUTH_TOKEN_RETRIES_DEFAULT;
import static com.tgt.crm.token.core.SalesforceConstants.RETRY_BACKOFF_DELAY_DEFAULT;
import static com.tgt.crm.token.core.SalesforceConstants.RETRY_BACKOFF_MULTIPLIER_DEFAULT;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("salesforce")
@Validated
public class SalesforceConfig {

  private static final String NOT_ENV_VAR_PATTERN = "^(?!(\\$\\{.+})$).+$";
  private static final String ENV_VAR_MISSING_MSG = "Environment variable is missing";

  @NotBlank
  @Pattern(regexp = NOT_ENV_VAR_PATTERN, message = ENV_VAR_MISSING_MSG)
  private String host;

  @NotBlank
  @Pattern(regexp = NOT_ENV_VAR_PATTERN, message = ENV_VAR_MISSING_MSG)
  private String username;

  @NotBlank
  @Pattern(regexp = NOT_ENV_VAR_PATTERN, message = ENV_VAR_MISSING_MSG)
  private String password;

  @NotBlank
  @Pattern(regexp = NOT_ENV_VAR_PATTERN, message = ENV_VAR_MISSING_MSG)
  private String clientId;

  @NotBlank
  @Pattern(regexp = NOT_ENV_VAR_PATTERN, message = ENV_VAR_MISSING_MSG)
  private String clientSecret;

  private String authUri = AUTH_URI;
  private int maxAuthTokenRetries = MAX_AUTH_TOKEN_RETRIES_DEFAULT;
  private int retryBackoffDelay = RETRY_BACKOFF_DELAY_DEFAULT; // milliseconds

  // property only used for MVC. WebClient retry uses the backoff delay value as a min delay and
  // a jitter factor to randomize retry delays instead of a fixed multiplier
  private int retryBackoffMultiplier = RETRY_BACKOFF_MULTIPLIER_DEFAULT;
}
