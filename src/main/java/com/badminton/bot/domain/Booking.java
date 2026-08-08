package com.badminton.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Запись одного человека (или компании) на диапазон получасовых слотов внутри события.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "username")
    private String username;

    /** Индекс начального получасового слота (0-based, 0 = SESSION_START). */
    @Column(name = "start_slot", nullable = false)
    private int startSlot;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Слот, следующий за последним занятым (exclusive), для удобства проверки пересечений. */
    public int endSlotExclusive(int slotStepMinutes) {
        return startSlot + durationMinutes / slotStepMinutes;
    }

    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.WAITLISTED;
    }
}
