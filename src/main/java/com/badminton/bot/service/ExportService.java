package com.badminton.bot.service;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.Event;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Резервный экспорт списка записей события в CSV (основной способ — таблица в чате). */
@Service
public class ExportService {

    private final SlotCalculator slotCalculator;
    private final PlayerSkillService playerSkillService;

    public ExportService(SlotCalculator slotCalculator, PlayerSkillService playerSkillService) {
        this.slotCalculator = slotCalculator;
        this.playerSkillService = playerSkillService;
    }

    public byte[] toCsv(Event event, List<Booking> bookings) {
        List<Long> userIds = bookings.stream().map(Booking::getTelegramUserId).toList();
        Map<Long, Long> minutes = playerSkillService.minutesPlayedBefore(userIds, event.getEventDate());
        Map<Long, Double> skills = playerSkillService.skillsBefore(userIds, event.getEventDate());
        StringBuilder sb = new StringBuilder();
        sb.append("Дата;Слот;Имя;Username;TelegramId;Часы;Скилл;Человек;Статус;Создано\n");
        String dateStr = event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        bookings.stream()
                .sorted(Comparator.comparing(Booking::getStartSlot).thenComparing(Booking::getCreatedAt))
                .forEach(b -> {
                    long mins = minutes.getOrDefault(b.getTelegramUserId(), 0L);
                    double skill = skills.getOrDefault(b.getTelegramUserId(), 0.0);
                    sb.append(csv(dateStr)).append(';')
                            .append(csv(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))).append(';')
                            .append(csv(b.getDisplayName())).append(';')
                            .append(csv(b.getUsername())).append(';')
                            .append(b.getTelegramUserId()).append(';')
                            .append(String.format(java.util.Locale.US, "%.1f", mins / 60.0)).append(';')
                            .append(SlotSkillModel.formatSkill(skill)).append(';')
                            .append(b.getPartySize()).append(';')
                            .append(b.getStatus()).append(';')
                            .append(b.getCreatedAt()).append('\n');
                });

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(";", ",");
    }
}
