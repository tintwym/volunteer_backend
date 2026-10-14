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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

  public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

  public enum Status { OPEN, RESOLVED }

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reported_by")
  private UserAccount reportedBy;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(columnDefinition = "TEXT")
  private String resolution;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolved_by")
  private UserAccount resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

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
  public Event getEvent() { return event; }
  public void setEvent(Event event) { this.event = event; }
  public UserAccount getReportedBy() { return reportedBy; }
  public void setReportedBy(UserAccount reportedBy) { this.reportedBy = reportedBy; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Severity getSeverity() { return severity; }
  public void setSeverity(Severity severity) { this.severity = severity; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public String getResolution() { return resolution; }
  public void setResolution(String resolution) { this.resolution = resolution; }
  public UserAccount getResolvedBy() { return resolvedBy; }
  public void setResolvedBy(UserAccount resolvedBy) { this.resolvedBy = resolvedBy; }
  public Instant getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
  public Instant getCreatedAt() { return createdAt; }
}
