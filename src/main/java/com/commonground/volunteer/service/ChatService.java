package com.commonground.volunteer.service;

import com.commonground.volunteer.config.GeminiProperties;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.ChatDtos.ChatMessage;
import com.commonground.volunteer.web.dto.ChatDtos.ChatRequest;
import com.commonground.volunteer.web.dto.ChatDtos.ChatResponse;
import com.commonground.volunteer.web.dto.EventDtos.EventResponse;
import com.commonground.volunteer.web.dto.EventDtos.RegistrationResponse;
import com.commonground.volunteer.web.dto.HourDtos.HourLogResponse;
import com.commonground.volunteer.web.dto.MemberDtos.MySummary;
import com.commonground.volunteer.web.dto.WorkDtos.TaskResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final int MAX_HISTORY = 12;
  private static final int MAX_LIST_ITEMS = 8;
  private static final String SYSTEM_PROMPT = """
      You are the CommonGround volunteer assistant inside the volunteer management app.
      Answer clearly and helpfully using ONLY the user context JSON provided with each turn.
      If the context does not contain the answer, say you do not have that information — never invent
      events, hours, registrations, or tasks.
      You cannot take actions (sign up, approve hours, check in). Suggest which screen to use instead.
      Keep replies concise (a few short paragraphs or a short bullet list).
      """.stripIndent();

  private final GeminiProperties geminiProperties;
  private final MemberService memberService;
  private final EventService eventService;
  private final TaskService taskService;
  private final HourService hourService;
  private final RestClient restClient;

  public ChatService(
      GeminiProperties geminiProperties,
      MemberService memberService,
      EventService eventService,
      TaskService taskService,
      HourService hourService
  ) {
    this.geminiProperties = geminiProperties;
    this.memberService = memberService;
    this.eventService = eventService;
    this.taskService = taskService;
    this.hourService = hourService;
    this.restClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .build();
  }

  public ChatResponse chat(UserPrincipal principal, ChatRequest request) {
    if (!geminiProperties.isReady()) {
      throw new ApiException("AI assistant is not configured on this server yet");
    }

    String context = buildContext(principal);
    List<Map<String, Object>> contents = new ArrayList<>();
    List<ChatMessage> history = request.history() == null ? List.of() : request.history();
    int start = Math.max(0, history.size() - MAX_HISTORY);
    for (ChatMessage turn : history.subList(start, history.size())) {
      String role = normalizeRole(turn.role());
      if (role == null) {
        continue;
      }
      String text = turn.content() == null ? "" : turn.content().trim();
      if (text.isEmpty()) {
        continue;
      }
      appendAlternating(contents, role, text);
    }
    appendAlternating(
        contents,
        "user",
        "User context (JSON):\n" + context + "\n\nUser question:\n" + request.message().trim()
    );
    // Gemini requires the first content turn to be from the user.
    if (!contents.isEmpty() && !"user".equals(contents.get(0).get("role"))) {
      contents.add(0, content("user", "Continue with the user context below."));
    }

    String model = geminiProperties.model() == null || geminiProperties.model().isBlank()
        ? "gemini-2.0-flash"
        : geminiProperties.model().trim();
    String path = "/v1beta/models/" + model + ":generateContent";

    try {
      GeminiResponse response = restClient.post()
          .uri(uriBuilder -> uriBuilder
              .path(path)
              .queryParam("key", geminiProperties.apiKey())
              .build())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(
              "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
              "contents", contents,
              "generationConfig", Map.of(
                  "temperature", 0.4,
                  "maxOutputTokens", 1024
              )
          ))
          .retrieve()
          .body(GeminiResponse.class);

      String reply = extractText(response);
      if (reply == null || reply.isBlank()) {
        throw new ApiException("The AI assistant returned an empty reply. Please try again.");
      }
      return new ChatResponse(reply.trim());
    } catch (ApiException e) {
      throw e;
    } catch (RestClientException e) {
      log.warn("Gemini request failed: {}", e.getMessage());
      throw new ApiException("Could not reach the AI assistant. Please try again in a moment.");
    } catch (Exception e) {
      log.warn("Gemini unexpected error: {}", e.getMessage());
      throw new ApiException("The AI assistant failed to respond. Please try again.");
    }
  }

  private String buildContext(UserPrincipal principal) {
    MySummary summary = memberService.mySummary(principal);
    List<RegistrationResponse> registrations = eventService.myRegistrations(principal).stream()
        .limit(MAX_LIST_ITEMS)
        .toList();
    List<EventResponse> upcoming = eventService.list(principal, true).stream()
        .limit(MAX_LIST_ITEMS)
        .toList();
    List<TaskResponse> tasks = taskService.mine(principal).stream()
        .limit(MAX_LIST_ITEMS)
        .toList();
    List<HourLogResponse> hours = hourService.mine(principal).stream()
        .limit(MAX_LIST_ITEMS)
        .toList();

    StringBuilder sb = new StringBuilder(2048);
    sb.append('{')
        .append("\"user\":{")
        .append("\"fullName\":").append(json(principal.fullName())).append(',')
        .append("\"email\":").append(json(principal.email())).append(',')
        .append("\"role\":").append(json(principal.role().name())).append(',')
        .append("\"organizationId\":").append(json(principal.organizationId() == null
            ? null : principal.organizationId().toString()))
        .append("},")
        .append("\"summary\":{")
        .append("\"totalHours\":").append(summary.totalHours()).append(',')
        .append("\"pendingHourLogs\":").append(summary.pendingHourLogs()).append(',')
        .append("\"upcomingRegistrations\":").append(summary.upcomingRegistrations()).append(',')
        .append("\"completedEvents\":").append(summary.completedEvents()).append(',')
        .append("\"openTasks\":").append(summary.openTasks()).append(',')
        .append("\"unreadNotifications\":").append(summary.unreadNotifications()).append(',')
        .append("\"currentBadge\":").append(summary.currentBadge() == null
            ? "null" : json(summary.currentBadge().title()))
        .append("},")
        .append("\"myRegistrations\":[");
    for (int i = 0; i < registrations.size(); i++) {
      RegistrationResponse r = registrations.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{')
          .append("\"eventName\":").append(json(r.eventName())).append(',')
          .append("\"eventDate\":").append(json(String.valueOf(r.eventDate()))).append(',')
          .append("\"status\":").append(json(r.status().name())).append(',')
          .append("\"venue\":").append(json(r.venue()))
          .append('}');
    }
    sb.append("],\"upcomingEvents\":[");
    for (int i = 0; i < upcoming.size(); i++) {
      EventResponse e = upcoming.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{')
          .append("\"name\":").append(json(e.name())).append(',')
          .append("\"date\":").append(json(String.valueOf(e.date()))).append(',')
          .append("\"startTime\":").append(json(String.valueOf(e.startTime()))).append(',')
          .append("\"venue\":").append(json(e.venue())).append(',')
          .append("\"status\":").append(json(e.status().name())).append(',')
          .append("\"myRegistrationStatus\":")
          .append(e.myRegistrationStatus() == null ? "null" : json(e.myRegistrationStatus().name()))
          .append('}');
    }
    sb.append("],\"myTasks\":[");
    for (int i = 0; i < tasks.size(); i++) {
      TaskResponse t = tasks.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{')
          .append("\"title\":").append(json(t.title())).append(',')
          .append("\"status\":").append(json(t.status().name())).append(',')
          .append("\"priority\":").append(json(t.priority() == null ? null : t.priority().name())).append(',')
          .append("\"dueDate\":").append(json(t.dueDate() == null ? null : String.valueOf(t.dueDate()))).append(',')
          .append("\"eventName\":").append(json(t.eventName()))
          .append('}');
    }
    sb.append("],\"recentHourLogs\":[");
    for (int i = 0; i < hours.size(); i++) {
      HourLogResponse h = hours.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{')
          .append("\"workDate\":").append(json(String.valueOf(h.workDate()))).append(',')
          .append("\"hours\":").append(h.hours()).append(',')
          .append("\"status\":").append(json(h.status().name())).append(',')
          .append("\"eventName\":").append(json(h.eventName())).append(',')
          .append("\"description\":").append(json(truncate(h.description(), 120)))
          .append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  private static Map<String, Object> content(String role, String text) {
    return Map.of("role", role, "parts", List.of(Map.of("text", text)));
  }

  /** Gemini rejects consecutive turns with the same role — merge or skip. */
  @SuppressWarnings("unchecked")
  private static void appendAlternating(List<Map<String, Object>> contents, String role, String text) {
    if (contents.isEmpty()) {
      contents.add(content(role, text));
      return;
    }
    Map<String, Object> last = contents.get(contents.size() - 1);
    if (role.equals(last.get("role"))) {
      List<Map<String, String>> parts = (List<Map<String, String>>) last.get("parts");
      String prev = parts == null || parts.isEmpty() ? "" : parts.get(0).getOrDefault("text", "");
      contents.set(contents.size() - 1, content(role, (prev + "\n\n" + text).trim()));
      return;
    }
    contents.add(content(role, text));
  }

  private static String normalizeRole(String role) {
    if (role == null) {
      return null;
    }
    String r = role.trim().toLowerCase(Locale.ROOT);
    return switch (r) {
      case "user" -> "user";
      case "assistant", "model" -> "model";
      default -> null;
    };
  }

  private static String extractText(GeminiResponse response) {
    if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
      return null;
    }
    GeminiContent content = response.candidates().get(0).content();
    if (content == null || content.parts() == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (GeminiPart part : content.parts()) {
      if (part.text() != null) {
        sb.append(part.text());
      }
    }
    return sb.toString();
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max) + "…";
  }

  private static String json(String value) {
    if (value == null) {
      return "null";
    }
    String escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
    return "\"" + escaped + "\"";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GeminiResponse(List<GeminiCandidate> candidates) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GeminiCandidate(GeminiContent content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GeminiContent(List<GeminiPart> parts, String role) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GeminiPart(@JsonProperty("text") String text) {}
}
