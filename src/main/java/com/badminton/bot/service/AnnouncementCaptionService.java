package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.Event;
import org.springframework.stereotype.Service;

import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Текст подписи к картинке анонса — в привычном формате «СБОР НА ЗАРЯДКУ…» плюс живая таблица.
 */
@Service
public class AnnouncementCaptionService {

    private static final int TELEGRAM_CAPTION_LIMIT = 1024;

    private final BadmintonProperties properties;
    private final TableRenderService tableRenderService;

    public AnnouncementCaptionService(BadmintonProperties properties, TableRenderService tableRenderService) {
        this.properties = properties;
        this.tableRenderService = tableRenderService;
    }

    public String render(Event event, List<Booking> bookings) {
        String intro = intro(event);
        String table = tableRenderService.renderSlotsOnly(event, bookings);
        String full = intro + "\n\n" + table;
        if (full.length() <= TELEGRAM_CAPTION_LIMIT) {
            return full;
        }
        // если таблица разрослась — обрезаем аккуратно
        int keep = TELEGRAM_CAPTION_LIMIT - intro.length() - 20;
        if (keep < 80) {
            return intro;
        }
        return intro + "\n\n" + table.substring(0, keep) + "…";
    }

    private String intro(Event event) {
        Locale ru = new Locale("ru");
        String day = event.getEventDate().getDayOfWeek().getDisplayName(TextStyle.FULL, ru).toUpperCase(ru);
        int dayOfMonth = event.getEventDate().getDayOfMonth();
        String month = event.getEventDate().getMonth().getDisplayName(TextStyle.FULL, ru).toUpperCase(ru);

        return "СБОР НА ЗАРЯДКУ В " + day + " " + dayOfMonth + "-ГО " + month + " 🏸\n\n"
                + "Зарядка будет с " + properties.sessionStart() + " до " + properties.sessionEnd() + ".\n\n"
                + "Если планируете прийти — нажмите «Записаться», бот продолжит в личке ⏱️\n\n"
                + "Обязательно возьмите с собой сменную спортивную обувь и питьевую воду.";
    }
}
