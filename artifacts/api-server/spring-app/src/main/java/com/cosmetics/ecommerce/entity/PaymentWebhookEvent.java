package com.cosmetics.ecommerce.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events", indexes = {
        @Index(name = "idx_webhook_event_id", columnList = "razorpayEventId", unique = true),
        @Index(name = "idx_webhook_event_type", columnList = "eventType"),
        @Index(name = "idx_webhook_status", columnList = "processingStatus")
})
public class PaymentWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String razorpayEventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String processingStatus;

    @Column(columnDefinition = "LONGTEXT")
    private String payload;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected PaymentWebhookEvent() {}

    public PaymentWebhookEvent(String razorpayEventId, String eventType, String processingStatus, String payload) {
        this.razorpayEventId = razorpayEventId;
        this.eventType = eventType;
        this.processingStatus = processingStatus;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRazorpayEventId() { return razorpayEventId; }
    public void setRazorpayEventId(String razorpayEventId) { this.razorpayEventId = razorpayEventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

