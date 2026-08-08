package com.badminton.bot.scheduler;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.CreatedBy;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Планировщик автопубликации анонсов (за {@code registration-lead-days} дней до каждого
 * дня расписания) и автозакрытия событий по окончании тренировочного окна.
 * Оба джоба идемпотентны: повторный запуск не создаёт дублей и не ломает уже закрытые события.
 */
@Slf4j
@Component
public class EventScheduler {

    private final EventService eventService;
    private final BadmintonProperties properties;

    public EventScheduler(EventService eventService, BadmintonProperties properties) {
        this.eventService = eventService;
        this.properties = properties;
    }

    /** Проверяет каждую минуту, не настало ли настроенное время публикации анонсов. */
    @Scheduled(cron = "0 * * * * *")
    public void publishScheduledEvents() {
        LocalTime now = LocalTime.now(properties.zoneId()).withSecond(0).withNano(0);
        if (!now.equals(properties.publishAtTime())) {
            return;
        }

        LocalDate today = LocalDate.now(properties.zoneId());
        LocalDate candidate = today.plusDays(properties.registrationLeadDays());

        if (properties.trainingDaysList().contains(candidate.getDayOfWeek())) {
            log.info("Планировщик: публикуем анонс на {} (T-{} от сегодня {})",
                    candidate, properties.registrationLeadDays(), today);
            eventService.createAndPublish(candidate, CreatedBy.AUTO);
        }
    }

    /** Каждые 10 минут закрывает события, чьё тренировочное окно уже завершилось. */
    @Scheduled(cron = "0 */10 * * * *")
    public void closeFinishedEvents() {
        LocalDate today = LocalDate.now(properties.zoneId());
        LocalTime now = LocalTime.now(properties.zoneId());

        for (Event event : eventService.findOpenEvents()) {
            boolean finished = event.getEventDate().isBefore(today)
                    || (event.getEventDate().isEqual(today) && !now.isBefore(properties.sessionEndTime()));
            if (finished) {
                log.info("Планировщик: автозакрытие события {} ({})", event.getId(), event.getEventDate());
                eventService.closeEvent(event.getId());
            }
        }
    }
}
