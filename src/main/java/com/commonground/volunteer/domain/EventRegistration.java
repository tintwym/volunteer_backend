package com.commonground.volunteer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event_registrations")
public class EventRegistration {

  public enum Status {
    CONFIRMED, WAITLIST, CANCELLED, CHECKED_IN, COMPLETED, NO_SHOW;

    /** Statuses that occupy one of the event's capacity slots. */
    public static final Set<Status> HOLDS_SEAT = EnumSet.of(CONFIRMED, CHECKED_IN, COMPLETED);
  }

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Status status;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "checked_out_at")
  private Instant checkedOutAt;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public Event getEvent() { return event; }
  public void setEvent(Event event) { this.event = event; }
  public UserAccount getUser() { return user; }
  public void setUser(UserAccount user) { this.user = user; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public Instant getCheckedInAt() { return checkedInAt; }
  public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
  public Instant getCheckedOutAt() { return checkedOutAt; }
  public void setCheckedOutAt(Instant checkedOutAt) { this.checkedOutAt = checkedOutAt; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
