package com.badminton.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Сохранённый вариант записи пользователя (один на аккаунт). */
@Entity
@Table(name = "booking_presets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingPreset {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "start_slot", nullable = false)
    private int startSlot;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
