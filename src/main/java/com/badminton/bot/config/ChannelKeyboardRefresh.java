package com.badminton.bot.config;

import com.badminton.bot.domain.Event;
import com.badminton.bot.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * После деплоя обновляет кнопку «Записаться» в уже опубликованных постах
 * (callback → deep-link в бота).
 */
@Slf4j
@Component
public class ChannelKeyboardRefresh {

    private final EventService eventService;

    public ChannelKeyboardRefresh(EventService eventService) {
        this.eventService = eventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOpenPosts() {
        for (Event event : eventService.findOpenEvents()) {
            try {
                eventService.refreshBookingMessage(event.getId());
            } catch (Exception e) {
                log.warn("Не удалось обновить пост события {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
