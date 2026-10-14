package com.commonground.volunteer.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts the {@code postgres://user:pass@host:port/db} URLs that hosts such as Render and Heroku
 * hand out, translating them into the JDBC URL and credentials Spring expects.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    String raw = environment.getProperty("DATABASE_URL");
    if (raw == null) {
      return;
    }
    raw = raw.trim();
    if (raw.startsWith("jdbc:postgresql://") && hasUserInfo(raw.substring("jdbc:".length()))) {
      // The PostgreSQL driver rejects credentials inside a JDBC URL (it reads "user:pass" as host:port).
      raw = raw.substring("jdbc:".length());
    } else if (!(raw.startsWith("postgres://") || raw.startsWith("postgresql://"))) {
      return;
    }
    URI uri = URI.create(raw);
    StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
    if (uri.getPort() > 0) {
      jdbc.append(':').append(uri.getPort());
    }
    jdbc.append(uri.getRawPath());
    if (uri.getRawQuery() != null) {
      jdbc.append('?').append(uri.getRawQuery());
    }

    Map<String, Object> props = new HashMap<>();
    props.put("spring.datasource.url", jdbc.toString());
    String userInfo = uri.getRawUserInfo();
    if (userInfo != null) {
      int colon = userInfo.indexOf(':');
      String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
      props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
      if (colon >= 0) {
        props.put("spring.datasource.password",
            URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
      }
    }
    environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
  }

  private static boolean hasUserInfo(String url) {
    int start = url.indexOf("//") + 2;
    int end = url.indexOf('/', start);
    String authority = end >= 0 ? url.substring(start, end) : url.substring(start);
    return authority.contains("@");
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
