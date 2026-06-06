package com.docconversion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, CorsProperties.class})
public class ApplicationPropertiesConfig {
}
