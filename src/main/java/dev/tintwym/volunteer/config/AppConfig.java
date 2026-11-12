package dev.tintwym.volunteer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  JwtProperties.class,
  CorsProperties.class,
  GeminiProperties.class,
  FirebaseProperties.class
})
public class AppConfig {}
