package dev.tintwym.volunteer.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import dev.tintwym.volunteer.config.FirebaseProperties;
import dev.tintwym.volunteer.domain.DeviceToken;
import dev.tintwym.volunteer.repository.DeviceTokenRepository;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FcmPushService {

  private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

  private final FirebaseProperties firebaseProperties;
  private final DeviceTokenRepository deviceTokenRepository;
  private boolean enabled;

  public FcmPushService(FirebaseProperties firebaseProperties, DeviceTokenRepository deviceTokenRepository) {
    this.firebaseProperties = firebaseProperties;
    this.deviceTokenRepository = deviceTokenRepository;
  }

  @PostConstruct
  void init() {
    String json = firebaseProperties.getCredentialsJson();
    if (json == null || json.isBlank()) {
      log.info("FCM push disabled — set app.firebase.credentials-json / FIREBASE_CREDENTIALS_JSON to enable");
      enabled = false;
      return;
    }
    try {
      if (FirebaseApp.getApps().isEmpty()) {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
            new ByteArrayInputStream(json.trim().getBytes(StandardCharsets.UTF_8)));
        FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build());
      }
      enabled = true;
      log.info("FCM push enabled");
    } catch (Exception ex) {
      enabled = false;
      log.warn("FCM push failed to initialize: {}", ex.getMessage());
    }
  }

  public void sendToUser(UUID userId, String title, String body, String link) {
    if (!enabled || userId == null) {
      return;
    }
    List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
    if (tokens.isEmpty()) {
      return;
    }
    for (DeviceToken row : tokens) {
      sendOne(row, title, body, link);
    }
  }

  private void sendOne(DeviceToken row, String title, String body, String link) {
    try {
      Message.Builder builder = Message.builder()
          .setToken(row.getToken())
          .setNotification(
              Notification.builder()
                  .setTitle(title == null ? "CommonGround" : title)
                  .setBody(body == null ? "" : body)
                  .build());
      if (link != null && !link.isBlank()) {
        builder.putData("link", link);
      }
      FirebaseMessaging.getInstance().send(builder.build());
    } catch (FirebaseMessagingException ex) {
      String code = ex.getMessagingErrorCode() == null ? "" : ex.getMessagingErrorCode().name();
      if ("UNREGISTERED".equals(code) || "INVALID_ARGUMENT".equals(code)) {
        log.info("Removing stale FCM token {} ({})", row.getId(), code);
        deviceTokenRepository.delete(row);
      } else {
        log.warn("FCM send failed for token {}: {}", row.getId(), ex.getMessage());
      }
    } catch (Exception ex) {
      log.warn("FCM send failed for token {}: {}", row.getId(), ex.getMessage());
    }
  }
}
