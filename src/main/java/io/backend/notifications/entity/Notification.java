package io.backend.notifications.entity;

import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotBlank
  @Column(nullable = false)
  private String title;

  @NotBlank
  @Column(nullable = false)
  private String content;

  @NotNull
  @Enumerated(EnumType.STRING)
  private Channel channel;

  @NotNull
  @Enumerated(EnumType.STRING)
  private Status status;

  @Column private LocalDateTime createdAt;

  @ElementCollection
  @CollectionTable(
      name = "notification_attachment_ids",
      joinColumns = @JoinColumn(name = "notification_id"))
  @Column(name = "attachment_id")
  private List<Long> attachmentIds = new ArrayList<>();

  @PrePersist
  public void onCreate() {
    createdAt = LocalDateTime.now();
    if (status == null) status = Status.PENDING;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public List<Long> getAttachmentIds() {
    return attachmentIds;
  }

  public void setAttachmentIds(List<Long> attachmentIds) {
    this.attachmentIds = attachmentIds;
  }
}
