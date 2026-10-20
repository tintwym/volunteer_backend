package com.commonground.volunteer.config;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.EventRegistration;
import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.domain.Incident;
import com.commonground.volunteer.domain.Message;
import com.commonground.volunteer.domain.Organization;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.Task;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.repository.EventRegistrationRepository;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.HourLogRepository;
import com.commonground.volunteer.repository.IncidentRepository;
import com.commonground.volunteer.repository.MessageRepository;
import com.commonground.volunteer.repository.OrganizationRepository;
import com.commonground.volunteer.repository.TaskRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one account per role (organizer, volunteer leader, volunteer) plus supporting
 * volunteer peers and sample activity. Sign in with email + {@link #DEMO_PASSWORD} —
 * there is no one-click demo login.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

  public static final String DEMO_PASSWORD = "Password123!";

  /** Canonical seeded accounts — one primary user per role. */
  public static final String ORGANIZER_EMAIL = "marcus@greenroots.org";
  public static final String LEADER_EMAIL = "jordan.lee@commonground.org";
  public static final String VOLUNTEER_EMAIL = "alex.rivera@commonground.org";

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final OrganizationRepository organizations;
  private final UserAccountRepository users;
  private final EventRepository events;
  private final EventRegistrationRepository registrations;
  private final HourLogRepository hours;
  private final TaskRepository tasks;
  private final IncidentRepository incidents;
  private final MessageRepository messages;
  private final NotificationService notifications;
  private final PasswordEncoder passwordEncoder;

  public DemoDataSeeder(
      OrganizationRepository organizations,
      UserAccountRepository users,
      EventRepository events,
      EventRegistrationRepository registrations,
      HourLogRepository hours,
      TaskRepository tasks,
      IncidentRepository incidents,
      MessageRepository messages,
      NotificationService notifications,
      PasswordEncoder passwordEncoder
  ) {
    this.organizations = organizations;
    this.users = users;
    this.events = events;
    this.registrations = registrations;
    this.hours = hours;
    this.tasks = tasks;
    this.incidents = incidents;
    this.messages = messages;
    this.notifications = notifications;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.existsByEmailIgnoreCase(ORGANIZER_EMAIL)) {
      // Already seeded: keep identities, fill missing profile details (e.g. phone).
      backfillVolunteerDetails();
      return;
    }

    Organization org = seedOrganization();
    UserAccount organizer = seedOrganizer(org);
    UserAccount leader = seedVolunteerLeader(org);
    UserAccount volunteer = seedVolunteer(org);
    UserAccount priya = seedSupportingVolunteer(org, "priya.nair@commonground.org", "Priya Nair", "+1 206 555 0104");
    UserAccount sam = seedSupportingVolunteer(org, "sam.okafor@commonground.org", "Sam Okafor", "+1 206 555 0105");

    seedSampleActivity(org, organizer, leader, volunteer, priya, sam);

    log.info(
        "Seeded role accounts for '{}' — organizer {}, leader {}, volunteer {} (password {})",
        org.getName(), ORGANIZER_EMAIL, LEADER_EMAIL, VOLUNTEER_EMAIL, DEMO_PASSWORD
    );
  }

  private void backfillVolunteerDetails() {
    ensurePhone(ORGANIZER_EMAIL, "+1 206 555 0101");
    ensurePhone(LEADER_EMAIL, "+1 206 555 0102");
    ensurePhone(VOLUNTEER_EMAIL, "+1 206 555 0103");
    ensurePhone("priya.nair@commonground.org", "+1 206 555 0104");
    ensurePhone("sam.okafor@commonground.org", "+1 206 555 0105");
  }

  private void ensurePhone(String email, String phone) {
    users.findByEmailIgnoreCase(email).ifPresent(user -> {
      if (user.getPhone() == null || user.getPhone().isBlank()) {
        user.setPhone(phone);
        users.save(user);
      }
    });
  }

  private Organization seedOrganization() {
    Organization org = new Organization();
    org.setName("Green Roots Collective");
    org.setSlug(organizations.existsBySlug("green-roots-collective") ? "green-roots-demo" : "green-roots-collective");
    org.setDescription("Community gardening, food rescue and park restoration.");
    return organizations.save(org);
  }

  /** Role: ORGANIZER — owns the organization and publishes events. */
  private UserAccount seedOrganizer(Organization org) {
    return user(org, ORGANIZER_EMAIL, "Marcus Vance", Role.ORGANIZER, "+1 206 555 0101");
  }

  /** Role: VOLUNTEER_LEADER — runs shifts, attendance, and team tasks. */
  private UserAccount seedVolunteerLeader(Organization org) {
    return user(org, LEADER_EMAIL, "Jordan Lee", Role.VOLUNTEER_LEADER, "+1 206 555 0102");
  }

  /** Role: VOLUNTEER — primary volunteer with hours, tasks, and registrations. */
  private UserAccount seedVolunteer(Organization org) {
    return user(org, VOLUNTEER_EMAIL, "Alex Rivera", Role.VOLUNTEER, "+1 206 555 0103");
  }

  private UserAccount seedSupportingVolunteer(Organization org, String email, String name, String phone) {
    return user(org, email, name, Role.VOLUNTEER, phone);
  }

  private void seedSampleActivity(
      Organization org,
      UserAccount marcus,
      UserAccount jordan,
      UserAccount alex,
      UserAccount priya,
      UserAccount sam
  ) {
    LocalDate today = LocalDate.now();
    Event past = event(org, marcus, "Riverside Park Cleanup", "Environment", "Riverside Park",
        today.minusDays(7), LocalTime.of(9, 0), LocalTime.of(13, 0), 20, Event.Status.COMPLETED);
    Event garden = event(org, marcus, "Community Garden Planting", "Environment", "Elm Street Garden",
        today.plusDays(3), LocalTime.of(8, 30), LocalTime.of(12, 0), 15, Event.Status.OPEN);
    Event food = event(org, marcus, "Food Bank Sorting Shift", "Food Security", "Downtown Food Bank",
        today.plusDays(5), LocalTime.of(14, 0), LocalTime.of(18, 0), 2, Event.Status.OPEN);
    event(org, marcus, "Youth Tutoring Night", "Education", "Central Library",
        today.plusDays(10), LocalTime.of(18, 0), LocalTime.of(20, 0), 10, Event.Status.OPEN);
    event(org, marcus, "Winter Coat Drive (planning)", "Community Aid", "Community Center",
        today.plusDays(30), LocalTime.of(10, 0), LocalTime.of(15, 0), 25, Event.Status.DRAFT);

    // Alex attended the cleanup and has earlier approved hours (28h total -> "Community Pillar").
    EventRegistration alexPast = registration(past, alex, EventRegistration.Status.COMPLETED);
    alexPast.setCheckedInAt(Instant.now().minus(7, ChronoUnit.DAYS));
    alexPast.setCheckedOutAt(alexPast.getCheckedInAt().plus(4, ChronoUnit.HOURS));
    hourLog(org, alex, past, alexPast, today.minusDays(7), "4", HourLog.Status.APPROVED, HourLog.Source.ATTENDANCE, jordan);
    hourLog(org, alex, null, null, today.minusDays(40), "12", HourLog.Status.APPROVED, HourLog.Source.MANUAL, marcus);
    hourLog(org, alex, null, null, today.minusDays(20), "12", HourLog.Status.APPROVED, HourLog.Source.MANUAL, marcus);
    registration(past, priya, EventRegistration.Status.NO_SHOW);
    hourLog(org, priya, null, null, today.minusDays(14), "6", HourLog.Status.APPROVED, HourLog.Source.MANUAL, jordan);
    hourLog(org, sam, null, null, today.minusDays(2), "3.5", HourLog.Status.PENDING, HourLog.Source.MANUAL, null);

    registration(garden, alex, EventRegistration.Status.CONFIRMED);
    registration(garden, jordan, EventRegistration.Status.CONFIRMED);
    registration(food, priya, EventRegistration.Status.CONFIRMED);
    registration(food, sam, EventRegistration.Status.CONFIRMED);

    task(org, marcus, garden, "Prepare tool checklist", Task.Priority.MEDIUM, jordan, today.plusDays(2));
    task(org, jordan, garden, "Bring seedlings from greenhouse", Task.Priority.HIGH, alex, today.plusDays(3));
    task(org, marcus, null, "Update volunteer handbook", Task.Priority.LOW, null, today.plusDays(14));

    Incident incident = new Incident();
    incident.setOrganization(org);
    incident.setEvent(past);
    incident.setReportedBy(jordan);
    incident.setTitle("Minor cut during cleanup");
    incident.setDescription("A volunteer cut their hand on debris; first aid applied on site.");
    incident.setSeverity(Incident.Severity.LOW);
    incident.setStatus(Incident.Status.OPEN);
    incidents.save(incident);

    message(org, marcus, Message.Channel.ANNOUNCEMENTS,
        "Welcome to Green Roots! Check the Events page for upcoming shifts.");
    message(org, jordan, Message.Channel.GENERAL, "Garden planting is on Saturday — gloves provided.");
    message(org, marcus, Message.Channel.LEADERS, "Leaders: please confirm your team rosters by Friday.");

    notifications.notify(alex, "BADGE_EARNED", "New badge: Community Pillar",
        "You reached 25 hours of volunteering.", "/badges");
    notifications.notify(alex, "TASK_ASSIGNED", "New task: Bring seedlings from greenhouse",
        "Due " + today.plusDays(3), "/tasks");
  }

  private UserAccount user(Organization org, String email, String name, Role role, String phone) {
    UserAccount u = new UserAccount();
    u.setEmail(email);
    u.setFullName(name);
    u.setRole(role);
    u.setPhone(phone);
    u.setOrganization(org);
    u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
    return users.save(u);
  }

  private Event event(Organization org, UserAccount creator, String name, String category, String venue,
      LocalDate date, LocalTime start, LocalTime end, int capacity, Event.Status status) {
    Event e = new Event();
    e.setOrganization(org);
    e.setCreatedBy(creator);
    e.setName(name);
    e.setCategory(category);
    e.setVenue(venue);
    e.setDescription(name + " with the Green Roots team.");
    e.setEventDate(date);
    e.setStartTime(start);
    e.setEndTime(end);
    e.setCapacity(capacity);
    e.setStatus(status);
    e.setCheckInCode("GR" + Math.abs(name.hashCode() % 10000));
    return events.save(e);
  }

  private EventRegistration registration(Event event, UserAccount user, EventRegistration.Status status) {
    EventRegistration r = new EventRegistration();
    r.setEvent(event);
    r.setUser(user);
    r.setStatus(status);
    return registrations.save(r);
  }

  private void hourLog(Organization org, UserAccount user, Event event, EventRegistration registration,
      LocalDate date, String amount, HourLog.Status status, HourLog.Source source, UserAccount reviewer) {
    HourLog h = new HourLog();
    h.setOrganization(org);
    h.setUser(user);
    h.setEvent(event);
    h.setRegistration(registration);
    h.setWorkDate(date);
    h.setHours(new BigDecimal(amount));
    h.setDescription(event != null ? event.getName() : "Weekend food rescue run");
    h.setStatus(status);
    h.setSource(source);
    if (reviewer != null) {
      h.setReviewedBy(reviewer);
      h.setReviewedAt(Instant.now());
    }
    hours.save(h);
  }

  private void task(Organization org, UserAccount creator, Event event, String title, Task.Priority priority,
      UserAccount assignee, LocalDate due) {
    Task t = new Task();
    t.setOrganization(org);
    t.setCreatedBy(creator);
    t.setEvent(event);
    t.setTitle(title);
    t.setPriority(priority);
    t.setStatus(Task.Status.TODO);
    t.setAssignee(assignee);
    t.setDueDate(due);
    tasks.save(t);
  }

  private void message(Organization org, UserAccount sender, Message.Channel channel, String content) {
    Message m = new Message();
    m.setOrganization(org);
    m.setSender(sender);
    m.setChannel(channel);
    m.setContent(content);
    messages.save(m);
  }
}
