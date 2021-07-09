package com.tgt.crm.token.webflux;

import com.tgt.crm.token.core.HttpClientConfig;
import com.tgt.crm.token.core.SalesforceConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SalesforceConfig.class, HttpClientConfig.class})
@ComponentScan
public class SalesforceLibraryAutoConfiguration {}
