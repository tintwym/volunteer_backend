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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

  public enum Status { DRAFT, OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED }

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 64)
  private String category;

  @Column(length = 200)
  private String venue;

  @Column(length = 500)
  private String address;

  @Column(name = "is_remote", nullable = false)
  private boolean remote;

  @Column(name = "meeting_link", length = 500)
  private String meetingLink;

  @Column(name = "event_date", nullable = false)
  private LocalDate eventDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(nullable = false)
  private int capacity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Status status;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "check_in_code", nullable = false, length = 16)
  private String checkInCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private UserAccount createdBy;

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
  public void setId(UUID id) { this.id = id; }
  public Organization getOrganization() { return organization; }
  public void setOrganization(Organization organization) { this.organization = organization; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getVenue() { return venue; }
  public void setVenue(String venue) { this.venue = venue; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public boolean isRemote() { return remote; }
  public void setRemote(boolean remote) { this.remote = remote; }
  public String getMeetingLink() { return meetingLink; }
  public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
  public LocalDate getEventDate() { return eventDate; }
  public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
  public LocalTime getStartTime() { return startTime; }
  public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
  public LocalTime getEndTime() { return endTime; }
  public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
  public int getCapacity() { return capacity; }
  public void setCapacity(int capacity) { this.capacity = capacity; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
  public String getCheckInCode() { return checkInCode; }
  public void setCheckInCode(String checkInCode) { this.checkInCode = checkInCode; }
  public UserAccount getCreatedBy() { return createdBy; }
  public void setCreatedBy(UserAccount createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
