package com.badminton.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Одна тренировка на конкретную дату. Может существовать несколько OPEN событий
 * одновременно, так как запись открывается за N дней до тренировки.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date", nullable = false, unique = true)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private CreatedBy createdBy;

    /** ID сообщения-анонса в канале. */
    @Column(name = "channel_message_id")
    private Integer channelMessageId;

    /** Чат группы обсуждений, куда Telegram зеркалит анонс. */
    @Column(name = "discussion_chat_id")
    private Long discussionChatId;

    /**
     * ID автоматически перезаписанного анонса (сообщения) в группе обсуждений — на него бот отвечает
     * ({@code reply_to_message_id}), чтобы попасть именно в комментарии к этому анонсу.
     */
    @Column(name = "discussion_anchor_message_id")
    private Integer discussionAnchorMessageId;

    /** message_thread_id форум-топика, если в группе обсуждений включены темы (topics). Обычно null. */
    @Column(name = "discussion_thread_id")
    private Integer discussionThreadId;

    /** ID служебного сообщения бота с кнопками записи и таблицей загрузки. */
    @Column(name = "booking_message_id")
    private Integer bookingMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isOpen() {
        return status == EventStatus.OPEN;
    }
}
