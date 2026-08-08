package com.badminton.bot.service;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.Event;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** Резервный экспорт списка записей события в CSV (основной способ — таблица в чате). */
@Service
public class ExportService {

    private final SlotCalculator slotCalculator;
    private final PlayerVisitService playerVisitService;

    public ExportService(SlotCalculator slotCalculator, PlayerVisitService playerVisitService) {
        this.slotCalculator = slotCalculator;
        this.playerVisitService = playerVisitService;
    }

    public byte[] toCsv(Event event, List<Booking> bookings) {
        var visits = playerVisitService.visitCountsBefore(
                bookings.stream().map(Booking::getTelegramUserId).toList(),
                event.getEventDate());
        StringBuilder sb = new StringBuilder();
        sb.append("Дата;Слот;Имя;Username;TelegramId;Находов;Человек;Статус;Создано\n");
        String dateStr = event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        bookings.stream()
                .sorted(Comparator.comparing(Booking::getStartSlot).thenComparing(Booking::getCreatedAt))
                .forEach(b -> sb.append(csv(dateStr)).append(';')
                        .append(csv(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))).append(';')
                        .append(csv(b.getDisplayName())).append(';')
                        .append(csv(b.getUsername())).append(';')
                        .append(b.getTelegramUserId()).append(';')
                        .append(visits.getOrDefault(b.getTelegramUserId(), 0)).append(';')
                        .append(b.getPartySize()).append(';')
                        .append(b.getStatus()).append(';')
                        .append(b.getCreatedAt()).append('\n'));

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(";", ",");
    }
}
