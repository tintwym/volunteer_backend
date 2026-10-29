package com.commonground.volunteer.config;

import com.commonground.volunteer.security.JwtAuthenticationFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final CorsProperties corsProperties;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CorsProperties corsProperties) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.corsProperties = corsProperties;
  }

  @Bean
  SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info"
            ).permitAll()
            .requestMatchers(
                "/api/auth/signup",
                "/api/auth/login",
                "/api/auth/accept-invite",
                "/api/auth/forgot-password",
                "/api/auth/reset-password",
                "/api/auth/google",
                "/api/auth/config"
            ).permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    String allowed = corsProperties.allowedOrigins();
    if (allowed == null || allowed.isBlank()) {
      allowed = "http://localhost:3000";
    }
    Set<String> patterns = new LinkedHashSet<>();
    Arrays.stream(allowed.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .forEach(patterns::add);
    // Vercel production + preview deployments, and local Next.js ports.
    patterns.add("https://*.vercel.app");
    patterns.add("http://localhost:*");
    patterns.add("http://127.0.0.1:*");
    config.setAllowedOriginPatterns(new ArrayList<>(patterns));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }
}
