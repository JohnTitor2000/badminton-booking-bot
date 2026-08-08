package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.config.TelegramProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.CreatedBy;
import com.badminton.bot.domain.Event;
import com.badminton.bot.domain.EventStatus;
import com.badminton.bot.repo.EventRepository;
import com.badminton.bot.telegram.KeyboardFactory;
import com.badminton.bot.telegram.TelegramSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Создание/публикация событий в канал: картинка «ЗАРЯДКА + дата» + подпись с таблицей и кнопкой.
 * Запись продолжается в личке с ботом.
 */
@Slf4j
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final BookingService bookingService;
    private final AnnouncementImageService announcementImageService;
    private final AnnouncementCaptionService announcementCaptionService;
    private final TelegramSender telegramSender;
    private final TelegramProperties telegramProperties;
    private final BadmintonProperties badmintonProperties;

    public EventService(EventRepository eventRepository,
                         BookingService bookingService,
                         AnnouncementImageService announcementImageService,
                         AnnouncementCaptionService announcementCaptionService,
                         TelegramSender telegramSender,
                         TelegramProperties telegramProperties,
                         BadmintonProperties badmintonProperties) {
        this.eventRepository = eventRepository;
        this.bookingService = bookingService;
        this.announcementImageService = announcementImageService;
        this.announcementCaptionService = announcementCaptionService;
        this.telegramSender = telegramSender;
        this.telegramProperties = telegramProperties;
        this.badmintonProperties = badmintonProperties;
    }

    public List<Event> findOpenEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(EventStatus.OPEN);
    }

    public Optional<Event> findByDate(LocalDate date) {
        return eventRepository.findByEventDate(date);
    }

    public Optional<Event> findById(Long eventId) {
        return eventRepository.findById(eventId);
    }

    @Transactional
    public Event createAndPublish(LocalDate date, CreatedBy createdBy) {
        Optional<Event> existing = eventRepository.findByEventDate(date);
        if (existing.isPresent()) {
            Event event = existing.get();
            if (event.getStatus() == EventStatus.CANCELLED) {
                event.setStatus(EventStatus.OPEN);
                event.setCreatedBy(createdBy);
                event.setChannelMessageId(null);
                event.setBookingMessageId(null);
                eventRepository.save(event);
                publishChannelPost(event);
                log.info("Событие на {} переоткрыто ({})", date, createdBy);
            } else if (event.getChannelMessageId() == null && event.getStatus() == EventStatus.OPEN) {
                publishChannelPost(event);
                log.info("Допубликовали пост в канал для события на {}", date);
            } else {
                log.info("Событие на {} уже существует со статусом {}, пропускаем создание", date, event.getStatus());
            }
            return event;
        }

        Event event = Event.builder()
                .eventDate(date)
                .status(EventStatus.OPEN)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();
        event = eventRepository.save(event);
        publishChannelPost(event);
        log.info("Создано и опубликовано событие на {} ({})", date, createdBy);
        return event;
    }

    public List<LocalDate> upcomingPublishableDates(int limit) {
        LocalDate today = LocalDate.now(badmintonProperties.zoneId());
        List<LocalDate> result = new java.util.ArrayList<>();
        LocalDate cursor = today;
        while (result.size() < limit) {
            if (badmintonProperties.trainingDaysList().contains(cursor.getDayOfWeek())) {
                Optional<Event> existing = eventRepository.findByEventDate(cursor);
                // не предлагаем дни с уже открытой (активной) публикацией
                boolean alreadyOpen = existing.isPresent() && existing.get().getStatus() == EventStatus.OPEN;
                if (!alreadyOpen) {
                    result.add(cursor);
                }
            }
            cursor = cursor.plusDays(1);
            if (cursor.isAfter(today.plusDays(60))) {
                break;
            }
        }
        return result;
    }

    private void publishChannelPost(Event event) {
        if (telegramProperties.channelId() == null) {
            log.warn("CHANNEL_ID не настроен, пост для события {} не опубликован", event.getId());
            return;
        }
        if (event.getChannelMessageId() != null) {
            return;
        }

        List<Booking> bookings = bookingService.activeBookings(event.getId());
        byte[] image = announcementImageService.render(event.getEventDate());
        String caption = announcementCaptionService.render(event, bookings);
        var keyboard = KeyboardFactory.entryKeyboard(event.getId(), telegramProperties.botUsername());
        String filename = "zaryadka-" + event.getEventDate().format(DateTimeFormatter.BASIC_ISO_DATE) + ".jpg";

        telegramSender.sendPhoto(telegramProperties.channelId(), image, filename, caption, keyboard)
                .ifPresent(message -> {
                    event.setChannelMessageId(message.getMessageId());
                    event.setBookingMessageId(message.getMessageId());
                    eventRepository.save(event);
                });
    }

    /** Обновляет подпись (таблицу) у фото-поста в канале. */
    @Transactional
    public void refreshBookingMessage(Long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        Long channelId = telegramProperties.channelId();
        if (event == null || channelId == null || event.getChannelMessageId() == null) {
            return;
        }
        List<Booking> bookings = bookingService.activeBookings(eventId);
        String caption = announcementCaptionService.render(event, bookings);
        var keyboard = event.isOpen()
                ? KeyboardFactory.entryKeyboard(event.getId(), telegramProperties.botUsername())
                : null;
        Integer previousMessageId = event.getChannelMessageId();
        var editResult = telegramSender.editCaptionResult(channelId, previousMessageId, caption, keyboard);
        if (editResult == TelegramSender.EditCaptionResult.OK
                || editResult == TelegramSender.EditCaptionResult.FAILED) {
            // FAILED: не плодим дубликат в канале — оставляем старый пост
            return;
        }
        // пост реально недоступен — публикуем заново (старый пытаемся убрать)
        telegramSender.deleteMessage(channelId, previousMessageId);
        event.setChannelMessageId(null);
        event.setBookingMessageId(null);
        eventRepository.save(event);
        if (event.isOpen()) {
            publishChannelPost(event);
        }
    }

    @Transactional
    public void closeEvent(Long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != EventStatus.OPEN) {
            return;
        }
        event.setStatus(EventStatus.CLOSED);
        eventRepository.save(event);
        refreshBookingMessage(eventId);
        if (telegramProperties.channelId() != null && event.getChannelMessageId() != null) {
            telegramSender.clearKeyboard(telegramProperties.channelId(), event.getChannelMessageId());
        }
    }

    @Transactional
    public Event cancelDate(LocalDate date) {
        Event event = eventRepository.findByEventDate(date).orElseGet(() -> Event.builder()
                .eventDate(date)
                .status(EventStatus.CANCELLED)
                .createdBy(CreatedBy.ADMIN)
                .createdAt(Instant.now())
                .build());

        boolean wasOpen = event.getStatus() == EventStatus.OPEN;
        event.setStatus(EventStatus.CANCELLED);
        event = eventRepository.save(event);

        if (wasOpen && telegramProperties.channelId() != null && event.getChannelMessageId() != null) {
            telegramSender.editCaption(telegramProperties.channelId(), event.getChannelMessageId(),
                    "🚫 Тренировка отменена администратором.", null);
            telegramSender.clearKeyboard(telegramProperties.channelId(), event.getChannelMessageId());
        }
        return event;
    }
}
