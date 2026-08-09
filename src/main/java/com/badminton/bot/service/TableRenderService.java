package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.domain.Event;
import com.badminton.bot.util.UserNames;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
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
            if (used == 0) {
                sb.append(" — свободно\n");
            } else {
                sb.append("\n");
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
            sb.append("\n«Записаться» / «Кто записан» — бот продолжит в личке.");
        }

        return sb.toString().trim();
    }

    /** Список записанных для лички (по кнопке с поста). */
    public String renderPlayersList(Event event, List<Booking> bookings) {
        String dateStr = event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        List<Booking> confirmed = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .sorted(Comparator.comparing(Booking::getStartSlot).thenComparing(Booking::getCreatedAt))
                .toList();
        List<Booking> waitlisted = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.WAITLISTED)
                .sorted(Comparator.comparing(Booking::getCreatedAt))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("👥 <b>Записанные на ").append(dateStr).append("</b>\n\n");
        if (confirmed.isEmpty()) {
            sb.append("Пока никто не записан.\n");
        } else {
            for (Booking b : confirmed) {
                sb.append(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))
                        .append(" — ").append(nameOf(b))
                        .append(" (").append(b.getPartySize()).append(" чел.)\n");
            }
        }
        if (!waitlisted.isEmpty()) {
            sb.append("\n⏳ <b>Лист ожидания:</b>\n");
            for (Booking b : waitlisted) {
                sb.append("• ").append(nameOf(b))
                        .append(" (").append(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))
                        .append(", ").append(b.getPartySize()).append(" чел.)\n");
            }
        }
        return sb.toString().trim();
    }

    private String nameOf(Booking b) {
        String linked = UserNames.mention(b.getDisplayName(), b.getTelegramUserId(), b.getUsername());
        return b.getPartySize() > 1 ? linked + " +" + (b.getPartySize() - 1) : linked;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
