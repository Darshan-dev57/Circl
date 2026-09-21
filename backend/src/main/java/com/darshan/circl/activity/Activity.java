package com.darshan.circl.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private UUID hostId;

    @Column(nullable = false, length = 80)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "seats_taken", nullable = false)
    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActivityStatus status = ActivityStatus.OPEN;

    @Column(name = "min_reliability", nullable = false)
    private int minReliability;

    @Column(name = "attendance_finalized", nullable = false)
    private boolean attendanceFinalized;

    @Column(name = "attendance_unreliable", nullable = false)
    private boolean attendanceUnreliable;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Activity() {
        // for JPA
    }

    public Activity(UUID hostId, String title, Category category, String description, double latitude, double longitude,
                    Instant startsAt, int durationMinutes, int capacity) {
        this.hostId = hostId;
        this.title = title;
        this.category = category;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.capacity = capacity;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public int seatsLeft() {
        return capacity - seatsTaken;
    }

    public Instant endsAt() {
        return startsAt.plus(Duration.ofMinutes(durationMinutes));
    }

    public boolean isOpen() {
        return status == ActivityStatus.OPEN;
    }

    public void cancel() {
        this.status = ActivityStatus.CANCELLED;
    }

    public boolean isHostedBy(UUID userId) {
        return hostId.equals(userId);
    }

    public UUID getId() { return id; }
    public UUID getHostId() { return hostId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public String getDescription() { return description; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getStartsAt() { return startsAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getCapacity() { return capacity; }
    public int getSeatsTaken() { return seatsTaken; }
    public ActivityStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setSeatsTaken(int seatsTaken) { this.seatsTaken = seatsTaken; }
    public int getMinReliability() { return minReliability; }
    public void setMinReliability(int minReliability) { this.minReliability = minReliability; }
    public boolean isAttendanceFinalized() { return attendanceFinalized; }
    public void markAttendanceFinalized() { this.attendanceFinalized = true; }
    public boolean isAttendanceUnreliable() { return attendanceUnreliable; }
    public void markAttendanceUnreliable() { this.attendanceUnreliable = true; }
}
