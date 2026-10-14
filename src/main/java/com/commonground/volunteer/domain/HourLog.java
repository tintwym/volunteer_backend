package com.commonground.volunteer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hour_logs")
public class HourLog {

  public enum Status { PENDING, APPROVED, REJECTED }

  public enum Source { ATTENDANCE, MANUAL }

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private Event event;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "registration_id")
  private EventRegistration registration;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal hours;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Source source;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private UserAccount reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() { return id; }
  public Organization getOrganization() { return organization; }
  public void setOrganization(Organization organization) { this.organization = organization; }
  public UserAccount getUser() { return user; }
  public void setUser(UserAccount user) { this.user = user; }
  public Event getEvent() { return event; }
  public void setEvent(Event event) { this.event = event; }
  public EventRegistration getRegistration() { return registration; }
  public void setRegistration(EventRegistration registration) { this.registration = registration; }
  public LocalDate getWorkDate() { return workDate; }
  public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
  public BigDecimal getHours() { return hours; }
  public void setHours(BigDecimal hours) { this.hours = hours; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public Source getSource() { return source; }
  public void setSource(Source source) { this.source = source; }
  public UserAccount getReviewedBy() { return reviewedBy; }
  public void setReviewedBy(UserAccount reviewedBy) { this.reviewedBy = reviewedBy; }
  public Instant getReviewedAt() { return reviewedAt; }
  public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
  public String getReviewNote() { return reviewNote; }
  public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
  public Instant getCreatedAt() { return createdAt; }
}
