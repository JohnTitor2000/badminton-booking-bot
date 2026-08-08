package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.domain.Event;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Рендерит таблицу загрузки события в текст, который бот показывает и обновляет
 * прямо в чате (одно сообщение на событие).
 */
@Component
public class TableRenderService {

    private final SlotCalculator slotCalculator;
    private final BadmintonProperties properties;

    public TableRenderService(SlotCalculator slotCalculator, BadmintonProperties properties) {
        this.slotCalculator = slotCalculator;
        this.properties = properties;
    }

    public String render(Event event, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();

        String dayName = event.getEventDate().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("ru"));
        String dateStr = event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        sb.append("🏸 <b>Бадминтон, ").append(capitalize(dayName)).append(" ").append(dateStr).append("</b>\n");
        sb.append(properties.sessionStart()).append("–").append(properties.sessionEnd())
                .append(", ").append(properties.courtsCount()).append(" корта\n\n");
        sb.append(renderSlotsOnly(event, bookings));
        return sb.toString();
    }

    /** Только сетка слотов и лист ожидания — для подписи к картинке анонса. */
    public String renderSlotsOnly(Event event, List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        int total = slotCalculator.totalSlots();
        int[] remaining = slotCalculator.remainingCapacityPerSlot(bookings);

        for (int slot = 0; slot < total; slot++) {
            String range = slotCalculator.formatSlotRange(slot, properties.slotStepMinutes());
            int used = properties.slotCapacity() - remaining[slot];
            sb.append("<b>").append(range).append("</b> [").append(used).append("/")
                    .append(properties.slotCapacity()).append("]");

            List<String> names = confirmedNamesForSlot(bookings, slot);
            if (names.isEmpty()) {
                sb.append(" — свободно\n");
            } else {
                sb.append(": ").append(String.join(", ", names)).append("\n");
            }
        }

        List<Booking> waitlisted = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.WAITLISTED)
                .sorted(Comparator.comparing(Booking::getCreatedAt))
                .toList();

        if (!waitlisted.isEmpty()) {
            sb.append("\n⏳ <b>Лист ожидания:</b>\n");
            for (Booking b : waitlisted) {
                sb.append("• ").append(nameOf(b))
                        .append(" (").append(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))
                        .append(", ").append(b.getPartySize()).append(" чел.)\n");
            }
        }

        if (event.isOpen()) {
            sb.append("\nНажмите «Записаться» — бот продолжит в личке.");
        }

        return sb.toString().trim();
    }

    private List<String> confirmedNamesForSlot(List<Booking> bookings, int slot) {
        List<String> names = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.getStatus() != BookingStatus.CONFIRMED) {
                continue;
            }
            int end = b.endSlotExclusive(properties.slotStepMinutes());
            if (slot >= b.getStartSlot() && slot < end) {
                names.add(nameOf(b));
            }
        }
        return names;
    }

    private String nameOf(Booking b) {
        return b.getPartySize() > 1 ? b.getDisplayName() + " +" + (b.getPartySize() - 1) : b.getDisplayName();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
