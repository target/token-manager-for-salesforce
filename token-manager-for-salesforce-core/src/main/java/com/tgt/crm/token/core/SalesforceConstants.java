package com.tgt.crm.token.core;

@SuppressWarnings("PMD.LongVariable")
public final class SalesforceConstants {

  public static final String AUTH_URI = "/services/oauth2/token";
  public static final String EXCEPTION_COUNTER = "exception_counter";
  public static final String EXCEPTION_TYPE_TAG = "exception_type";
  public static final String TOKEN_REFRESH_EXCEPTION = "token_refresh_exception";
  public static final int MAX_AUTH_TOKEN_RETRIES_DEFAULT = 3;
  public static final int RETRY_BACKOFF_DELAY_DEFAULT = 1000;
  public static final int RETRY_BACKOFF_MULTIPLIER_DEFAULT = 2;

  private SalesforceConstants() {}
}
