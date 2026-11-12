package dev.tintwym.volunteer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {

  /**
   * Full Firebase/Google service-account JSON. When empty, FCM push is disabled
   * and in-app notifications still work.
   */
  private String credentialsJson = "";

  public String getCredentialsJson() {
    return credentialsJson;
  }

  public void setCredentialsJson(String credentialsJson) {
    this.credentialsJson = credentialsJson;
  }
}
