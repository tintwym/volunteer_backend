package com.commonground.volunteer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

  private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

  @Test
  void translatesRenderStyleUrl() {
    StandardEnvironment env = envWith("postgresql://cg_user:p%40ss@dpg-abc-a.oregon-postgres.render.com/commonground");
    processor.postProcessEnvironment(env, null);
    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://dpg-abc-a.oregon-postgres.render.com/commonground");
    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("cg_user");
    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss");
  }

  @Test
  void keepsPortAndQuery() {
    StandardEnvironment env = envWith("postgres://u:p@host:6543/db?sslmode=require");
    processor.postProcessEnvironment(env, null);
    assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host:6543/db?sslmode=require");
  }

  @Test
  void leavesJdbcUrlsAlone() {
    StandardEnvironment env = envWith("jdbc:postgresql://localhost:5432/commonground");
    processor.postProcessEnvironment(env, null);
    assertThat(env.getProperty("spring.datasource.url")).isNull();
  }

  private StandardEnvironment envWith(String databaseUrl) {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("DATABASE_URL", databaseUrl)));
    return env;
  }
}
