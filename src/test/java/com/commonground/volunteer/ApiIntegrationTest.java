package com.commonground.volunteer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.volunteer.domain.PasswordResetToken;
import com.commonground.volunteer.repository.PasswordResetTokenRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PasswordResetTokenRepository resetTokens;
  @Autowired UserAccountRepository users;
  @Autowired JdbcTemplate jdbc;

  // ------------------------------------------------------------------ tests

  @Test
  void registrationWaitlistAttendanceAndBadges() throws Exception {
    Org org = newOrg();
    String eventId = createEvent(org.organizer, LocalDate.now(), 1, "OPEN");

    call(post("/api/events/" + eventId + "/register"), org.volunteer).andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    call(post("/api/events/" + eventId + "/register"), org.volunteer2).andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("WAITLIST"));
    call(post("/api/events/" + eventId + "/register"), org.volunteer).andExpect(status().isBadRequest());

    call(get("/api/events/" + eventId), org.volunteer)
        .andExpect(jsonPath("$.registeredCount").value(1))
        .andExpect(jsonPath("$.waitlistCount").value(1))
        .andExpect(jsonPath("$.myRegistrationStatus").value("CONFIRMED"));

    // Cancelling the confirmed seat promotes the waitlisted volunteer.
    call(delete("/api/events/" + eventId + "/register"), org.volunteer).andExpect(status().isNoContent());
    JsonNode regs = read(call(get("/api/events/" + eventId + "/registrations"), org.leader).andExpect(status().isOk()));
    JsonNode promoted = find(regs, "userEmail", org.volunteer2Email);
    assertThat(promoted.get("status").asText()).isEqualTo("CONFIRMED");
    String regId = promoted.get("id").asText();

    call(post("/api/events/" + eventId + "/registrations/" + regId + "/check-in"), org.leader)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
    call(post("/api/events/" + eventId + "/registrations/" + regId + "/check-out"), org.leader)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.hours").value(0.25));

    call(get("/api/hours/mine"), org.volunteer2)
        .andExpect(jsonPath("$[0].status").value("APPROVED"))
        .andExpect(jsonPath("$[0].source").value("ATTENDANCE"));
    call(get("/api/me/badges"), org.volunteer2)
        .andExpect(jsonPath("$.totalHours").value(0.25))
        .andExpect(jsonPath("$.badges[0].earned").value(false))
        .andExpect(jsonPath("$.next.title").value("First Step Volunteer"));
    call(get("/api/me/notifications"), org.volunteer2)
        .andExpect(jsonPath("$.items[?(@.title == 'A spot opened up: Test Event')]").exists());
  }

  @Test
  void hourApprovalAwardsBadgeAndEnforcesReviewerRules() throws Exception {
    Org org = newOrg();
    String logId = read(call(post("/api/hours"), org.volunteer, Map.of(
        "workDate", LocalDate.now().minusDays(1).toString(), "hours", 12, "description", "Food rescue"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asText();

    call(post("/api/hours/" + logId + "/review"), org.volunteer2, Map.of("approve", true))
        .andExpect(status().isForbidden());
    call(post("/api/hours/" + logId + "/review"), org.leader, Map.of("approve", true, "hours", 10.5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.hours").value(10.5));
    call(post("/api/hours/" + logId + "/review"), org.organizer, Map.of("approve", false))
        .andExpect(status().isBadRequest());

    call(get("/api/me/badges"), org.volunteer)
        .andExpect(jsonPath("$.current.title").value("Bronze Helper"))
        .andExpect(jsonPath("$.next.title").value("Community Pillar"));
    call(get("/api/me/notifications"), org.volunteer)
        .andExpect(jsonPath("$.items[?(@.title == 'New badge: Bronze Helper')]").exists())
        .andExpect(jsonPath("$.items[?(@.title == 'New badge: First Step Volunteer')]").exists());

    String leaderLog = read(call(post("/api/hours"), org.leader, Map.of(
        "workDate", LocalDate.now().toString(), "hours", 2))).get("id").asText();
    call(post("/api/hours/" + leaderLog + "/review"), org.leader, Map.of("approve", true))
        .andExpect(status().isForbidden());

    call(post("/api/hours"), org.volunteer, Map.of("workDate", LocalDate.now().plusDays(2).toString(), "hours", 2))
        .andExpect(status().isBadRequest());
    call(post("/api/hours"), org.volunteer, Map.of("workDate", LocalDate.now().toString(), "hours", 30))
        .andExpect(status().isBadRequest());
  }

  @Test
  void selfCheckInRequiresCodeAndCreatesPendingHours() throws Exception {
    Org org = newOrg();
    String eventId = createEvent(org.organizer, LocalDate.now(), 10, "OPEN");
    String code = read(call(get("/api/events/" + eventId), org.leader)).get("checkInCode").asText();
    call(get("/api/events/" + eventId), org.volunteer).andExpect(jsonPath("$.checkInCode").isEmpty());

    call(post("/api/events/" + eventId + "/register"), org.volunteer).andExpect(status().isCreated());
    call(post("/api/events/" + eventId + "/check-in"), org.volunteer, Map.of("code", "WRONG1"))
        .andExpect(status().isBadRequest());
    call(post("/api/events/" + eventId + "/check-in"), org.volunteer, Map.of("code", code.toLowerCase()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
    call(post("/api/events/" + eventId + "/check-out"), org.volunteer)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    call(get("/api/hours?status=PENDING"), org.organizer)
        .andExpect(jsonPath("$[?(@.source == 'ATTENDANCE')].userName").value(org.volunteerName));
  }

  @Test
  void rolePermissionsAndVisibility() throws Exception {
    Org org = newOrg();
    String draftId = createEvent(org.organizer, LocalDate.now().plusDays(3), 5, "DRAFT");

    mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    call(post("/api/events"), org.volunteer, eventBody(LocalDate.now(), 5, "OPEN")).andExpect(status().isForbidden());
    call(post("/api/events"), org.leader, eventBody(LocalDate.now(), 5, "OPEN")).andExpect(status().isForbidden());
    call(get("/api/events/" + draftId), org.volunteer).andExpect(status().isNotFound());
    call(get("/api/events"), org.volunteer).andExpect(jsonPath("$[?(@.id == '" + draftId + "')]").isEmpty());
    call(get("/api/events"), org.leader).andExpect(jsonPath("$[?(@.id == '" + draftId + "')]").isNotEmpty());
    call(post("/api/events/" + draftId + "/register"), org.leader).andExpect(status().isBadRequest());
    call(get("/api/members"), org.volunteer).andExpect(status().isForbidden());
    call(get("/api/dashboard/organization"), org.volunteer).andExpect(status().isForbidden());
    call(get("/api/dashboard/organization"), org.organizer).andExpect(status().isOk())
        .andExpect(jsonPath("$.activeVolunteers").value(2))
        .andExpect(jsonPath("$.activeLeaders").value(1));

    call(post("/api/events"), org.organizer, Map.of("name", "Bad times", "date", LocalDate.now().toString(),
        "startTime", "12:00", "endTime", "11:00", "capacity", 5)).andExpect(status().isBadRequest());
  }

  @Test
  void organizationsAreIsolated() throws Exception {
    Org a = newOrg();
    Org b = newOrg();
    String eventId = createEvent(a.organizer, LocalDate.now().plusDays(1), 5, "OPEN");
    String logId = read(call(post("/api/hours"), a.volunteer, Map.of(
        "workDate", LocalDate.now().toString(), "hours", 1))).get("id").asText();
    String aVolunteerId = read(call(get("/api/auth/me"), a.volunteer)).get("id").asText();

    call(get("/api/events/" + eventId), b.organizer).andExpect(status().isNotFound());
    call(post("/api/events/" + eventId + "/register"), b.volunteer).andExpect(status().isNotFound());
    call(post("/api/hours/" + logId + "/review"), b.organizer, Map.of("approve", true)).andExpect(status().isNotFound());
    call(patch("/api/members/" + aVolunteerId), b.organizer, Map.of("active", false)).andExpect(status().isNotFound());
    call(post("/api/tasks"), b.organizer, Map.of("title", "x", "assigneeId", aVolunteerId))
        .andExpect(status().isNotFound());
    call(get("/api/events"), b.volunteer).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void tasksIncidentsAndMessages() throws Exception {
    Org org = newOrg();
    String volunteerId = read(call(get("/api/auth/me"), org.volunteer)).get("id").asText();

    String taskId = read(call(post("/api/tasks"), org.leader, Map.of(
        "title", "Set up tables", "priority", "HIGH", "assigneeId", volunteerId))
        .andExpect(status().isCreated())).get("id").asText();
    call(get("/api/me/tasks"), org.volunteer).andExpect(jsonPath("$[0].title").value("Set up tables"));
    call(get("/api/tasks"), org.volunteer2).andExpect(jsonPath("$").isEmpty());
    call(patch("/api/tasks/" + taskId + "/status"), org.volunteer2, Map.of("status", "DONE"))
        .andExpect(status().isForbidden());
    call(patch("/api/tasks/" + taskId + "/status"), org.volunteer, Map.of("status", "DONE"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
    call(delete("/api/tasks/" + taskId), org.volunteer).andExpect(status().isForbidden());
    call(delete("/api/tasks/" + taskId), org.organizer).andExpect(status().isNoContent());

    String incidentId = read(call(post("/api/incidents"), org.volunteer, Map.of(
        "title", "Broken ladder", "description", "Rung snapped", "severity", "HIGH"))
        .andExpect(status().isCreated())).get("id").asText();
    call(get("/api/incidents"), org.volunteer2).andExpect(jsonPath("$").isEmpty());
    call(get("/api/me/notifications"), org.leader)
        .andExpect(jsonPath("$.items[?(@.title == '[HIGH] Broken ladder')]").exists());
    call(post("/api/incidents/" + incidentId + "/resolve"), org.volunteer, Map.of("resolution", "x"))
        .andExpect(status().isForbidden());
    call(post("/api/incidents/" + incidentId + "/resolve"), org.leader, Map.of("resolution", "Ladder replaced"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

    call(post("/api/messages/ANNOUNCEMENTS"), org.volunteer, Map.of("content", "hi")).andExpect(status().isForbidden());
    call(get("/api/messages/LEADERS"), org.volunteer).andExpect(status().isForbidden());
    call(post("/api/messages/GENERAL"), org.volunteer, Map.of("content", "Hello team")).andExpect(status().isCreated());
    call(post("/api/messages/ANNOUNCEMENTS"), org.organizer, Map.of("content", "Big news")).andExpect(status().isCreated());
    call(get("/api/messages/GENERAL"), org.volunteer2).andExpect(jsonPath("$[0].content").value("Hello team"));
    call(get("/api/me/notifications"), org.volunteer2)
        .andExpect(jsonPath("$.items[?(@.body == 'Big news')]").exists());
    call(get("/api/messages/NOPE"), org.volunteer).andExpect(status().isBadRequest());
  }

  @Test
  void organizerManagesMembers() throws Exception {
    Org org = newOrg();
    String volunteerId = read(call(get("/api/auth/me"), org.volunteer)).get("id").asText();
    String organizerId = read(call(get("/api/auth/me"), org.organizer)).get("id").asText();

    call(patch("/api/members/" + volunteerId), org.leader, Map.of("role", "VOLUNTEER_LEADER"))
        .andExpect(status().isForbidden());
    call(patch("/api/members/" + organizerId), org.organizer, Map.of("active", false))
        .andExpect(status().isForbidden());
    call(patch("/api/members/" + volunteerId), org.organizer, Map.of("role", "ORGANIZER"))
        .andExpect(status().isBadRequest());
    call(patch("/api/members/" + volunteerId), org.organizer, Map.of("role", "VOLUNTEER_LEADER"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VOLUNTEER_LEADER"));

    call(patch("/api/members/" + volunteerId), org.organizer, Map.of("active", false)).andExpect(status().isOk());
    call(get("/api/auth/me"), org.volunteer).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", org.volunteerEmail, "password", PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Account is disabled"));
  }

  @Test
  void passwordResetAndProfile() throws Exception {
    Org org = newOrg();
    postJson("/api/auth/forgot-password", Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com"))
        .andExpect(status().isOk());
    postJson("/api/auth/forgot-password", Map.of("email", org.volunteerEmail)).andExpect(status().isOk());

    String raw = "known-test-token-" + UUID.randomUUID();
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(users.findByEmailIgnoreCase(org.volunteerEmail).orElseThrow());
    token.setTokenHash(sha256(raw));
    token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    resetTokens.save(token);

    postJson("/api/auth/reset-password", Map.of("token", raw, "password", "short")).andExpect(status().isBadRequest());
    postJson("/api/auth/reset-password", Map.of("token", raw, "password", "BrandNew123!")).andExpect(status().isOk());
    postJson("/api/auth/reset-password", Map.of("token", raw, "password", "Another123!")).andExpect(status().isBadRequest());
    postJson("/api/auth/login", Map.of("email", org.volunteerEmail, "password", PASSWORD)).andExpect(status().isBadRequest());
    String fresh = read(postJson("/api/auth/login", Map.of("email", org.volunteerEmail, "password", "BrandNew123!"))
        .andExpect(status().isOk())).get("accessToken").asText();

    call(patch("/api/auth/me"), fresh, Map.of("fullName", "Renamed Person", "phone", "+65 9123 4567"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+65 9123 4567"));
    call(post("/api/auth/change-password"), fresh, Map.of("currentPassword", "wrong", "newPassword", "Whatever123!"))
        .andExpect(status().isBadRequest());
    // Token issue times have one-second precision; make sure the old token predates the change.
    Thread.sleep(1100);
    String renewed = read(call(post("/api/auth/change-password"), fresh,
        Map.of("currentPassword", "BrandNew123!", "newPassword", "Final123!"))
        .andExpect(status().isOk())).get("accessToken").asText();
    call(get("/api/auth/me"), fresh).andExpect(status().isUnauthorized());
    call(get("/api/auth/me"), renewed).andExpect(status().isOk());
  }

  @Test
  void passwordResetRevokesExistingSessions() throws Exception {
    Org org = newOrg();
    Thread.sleep(1100);
    String raw = "revoke-test-token-" + UUID.randomUUID();
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(users.findByEmailIgnoreCase(org.volunteerEmail).orElseThrow());
    token.setTokenHash(sha256(raw));
    token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    resetTokens.save(token);
    postJson("/api/auth/reset-password", Map.of("token", raw, "password", "Reset1234!")).andExpect(status().isOk());
    call(get("/api/auth/me"), org.volunteer).andExpect(status().isUnauthorized());
  }

  @Test
  void repeatedFailedLoginsAreThrottled() throws Exception {
    Org org = newOrg();
    for (int i = 0; i < 10; i++) {
      postJson("/api/auth/login", Map.of("email", org.volunteerEmail, "password", "wrong-" + i))
          .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
    postJson("/api/auth/login", Map.of("email", org.volunteerEmail, "password", PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Try again in a few minutes."));
  }

  @Test
  void raisingCapacityPromotesWaitlistInOrder() throws Exception {
    Org org = newOrg();
    String eventId = createEvent(org.organizer, LocalDate.now().plusDays(2), 1, "OPEN");
    call(post("/api/events/" + eventId + "/register"), org.leader).andExpect(jsonPath("$.status").value("CONFIRMED"));
    call(post("/api/events/" + eventId + "/register"), org.volunteer).andExpect(jsonPath("$.status").value("WAITLIST"));
    call(post("/api/events/" + eventId + "/register"), org.volunteer2).andExpect(jsonPath("$.status").value("WAITLIST"));
    // Re-joining the waitlist moves you to the back of the queue.
    call(delete("/api/events/" + eventId + "/register"), org.volunteer).andExpect(status().isNoContent());
    call(post("/api/events/" + eventId + "/register"), org.volunteer).andExpect(jsonPath("$.status").value("WAITLIST"));

    call(put("/api/events/" + eventId), org.organizer, eventBody(LocalDate.now().plusDays(2), 2, "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registeredCount").value(2))
        .andExpect(jsonPath("$.waitlistCount").value(1));
    call(get("/api/events/" + eventId), org.volunteer2).andExpect(jsonPath("$.myRegistrationStatus").value("CONFIRMED"));
    call(get("/api/events/" + eventId), org.volunteer).andExpect(jsonPath("$.myRegistrationStatus").value("WAITLIST"));
  }

  @Test
  void onlyStaffCanSendUrgentMessages() throws Exception {
    Org org = newOrg();
    call(post("/api/messages/GENERAL"), org.volunteer, Map.of("content", "Everyone look!", "urgent", true))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.urgent").value(false));
    call(get("/api/me/notifications"), org.volunteer2).andExpect(jsonPath("$.items").isEmpty());
    call(post("/api/messages/GENERAL"), org.leader, Map.of("content", "Weather alert", "urgent", true))
        .andExpect(jsonPath("$.urgent").value(true));
    call(get("/api/me/notifications"), org.volunteer2)
        .andExpect(jsonPath("$.items[?(@.body == 'Weather alert')]").exists());
  }

  @Test
  void volunteersCannotCancelTasks() throws Exception {
    Org org = newOrg();
    String volunteerId = read(call(get("/api/auth/me"), org.volunteer)).get("id").asText();
    String taskId = read(call(post("/api/tasks"), org.leader, Map.of("title", "Sweep", "assigneeId", volunteerId)))
        .get("id").asText();
    call(patch("/api/tasks/" + taskId + "/status"), org.volunteer, Map.of("status", "CANCELLED"))
        .andExpect(status().isForbidden());
  }

  @Test
  void googleSignInIsDisabledUntilConfigured() throws Exception {
    mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
        .andExpect(jsonPath("$.googleEnabled").value(false));
    postJson("/api/auth/google", Map.of("idToken", "fake")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Google sign-in is not configured on this server yet"));
  }

  @Test
  void eventTimesAreStoredExactlyAsEntered() throws Exception {
    TimeZone original = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
    try {
      Org org = newOrg();
      Map<String, Object> body = eventBody(LocalDate.now().plusDays(1), 5, "OPEN");
      body.put("startTime", "06:00");
      body.put("endTime", "08:00");
      String id = read(call(post("/api/events"), org.organizer, body).andExpect(status().isCreated()))
          .get("id").asText();
      String stored = jdbc.queryForObject(
          "SELECT start_time::text || '-' || end_time::text FROM events WHERE id = ?::uuid", String.class, id);
      assertThat(stored).isEqualTo("06:00:00-08:00:00");
      call(get("/api/events/" + id), org.volunteer).andExpect(jsonPath("$.startTime").value("06:00:00"));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  // ---------------------------------------------------------------- helpers

  private static final String PASSWORD = "Password123!";

  private record Org(
      String organizer, String leader, String volunteer, String volunteer2,
      String volunteerEmail, String volunteer2Email, String volunteerName) {}

  private Org newOrg() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String organizer = read(postJson("/api/auth/signup", Map.of(
        "fullName", "Olivia Organizer", "email", "org-" + suffix + "@example.com",
        "password", PASSWORD, "organizationName", "Org " + suffix))
        .andExpect(status().isCreated())).get("accessToken").asText();
    String leader = join(organizer, "leader-" + suffix + "@example.com", "VOLUNTEER_LEADER", "Lee Leader");
    String volunteerEmail = "vol-" + suffix + "@example.com";
    String volunteer = join(organizer, volunteerEmail, "VOLUNTEER", "Val Volunteer");
    String volunteer2Email = "vol2-" + suffix + "@example.com";
    String volunteer2 = join(organizer, volunteer2Email, "VOLUNTEER", "Vic Volunteer");
    return new Org(organizer, leader, volunteer, volunteer2, volunteerEmail, volunteer2Email, "Val Volunteer");
  }

  private String join(String organizer, String email, String role, String name) throws Exception {
    String inviteToken = read(call(post("/api/invitations"), organizer, Map.of("email", email, "role", role))
        .andExpect(status().isCreated())).get("token").asText();
    return read(postJson("/api/auth/accept-invite", Map.of("token", inviteToken, "fullName", name, "password", PASSWORD))
        .andExpect(status().isCreated())).get("accessToken").asText();
  }

  private String createEvent(String token, LocalDate date, int capacity, String status) throws Exception {
    return read(call(post("/api/events"), token, eventBody(date, capacity, status))
        .andExpect(status().isCreated())).get("id").asText();
  }

  private Map<String, Object> eventBody(LocalDate date, int capacity, String status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Test Event");
    body.put("category", "Environment");
    body.put("venue", "Park");
    body.put("date", date.toString());
    body.put("startTime", "09:00");
    body.put("endTime", "12:30");
    body.put("capacity", capacity);
    body.put("status", status);
    return body;
  }

  private ResultActions postJson(String url, Object body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static JsonNode find(JsonNode array, String field, String value) {
    for (JsonNode node : array) {
      if (value.equals(node.path(field).asText())) {
        return node;
      }
    }
    throw new AssertionError("No element with " + field + "=" + value + " in " + array);
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
