package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Чистая логика сетки слотов: константы площадки фиксированы (8:00-11:00, 2 корта,
 * получасовые слоты вместимостью 8 человек), поэтому вся арифметика по слотам не
 * зависит от конкретного события.
 */
@Component
public class SlotCalculator {

    private final BadmintonProperties properties;

    public SlotCalculator(BadmintonProperties properties) {
        this.properties = properties;
    }

    /** Варианты длительности с шагом 30 минут: от 30 минут до всего окна. */
    public List<Integer> durationOptionsMinutes() {
        int step = properties.slotStepMinutes();
        int totalMinutes = properties.slotsCount() * step;
        List<Integer> options = new ArrayList<>();
        for (int minutes = step; minutes <= totalMinutes; minutes += step) {
            options.add(minutes);
        }
        return options;
    }

    public LocalTime slotStartTime(int slotIndex) {
        return properties.sessionStartTime().plusMinutes((long) slotIndex * properties.slotStepMinutes());
    }

    public int totalSlots() {
        return properties.slotsCount();
    }

    public int slotsForDuration(int durationMinutes) {
        return durationMinutes / properties.slotStepMinutes();
    }

    /** Начальные слоты, на которые в принципе можно записаться на такую длительность, не выходя за окно. */
    public List<Integer> possibleStartSlots(int durationMinutes) {
        int span = slotsForDuration(durationMinutes);
        int total = totalSlots();
        List<Integer> result = new ArrayList<>();
        for (int start = 0; start + span <= total; start++) {
            result.add(start);
        }
        return result;
    }

    /**
     * Свободные места в каждом получасовом слоте (0..totalSlots-1) с учётом уже
     * подтверждённых записей. Лист ожидания (WAITLISTED) вместимость не занимает.
     */
    public int[] remainingCapacityPerSlot(List<Booking> confirmedBookings) {
        int total = totalSlots();
        int[] used = new int[total];
        for (Booking booking : confirmedBookings) {
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                continue;
            }
            int end = booking.endSlotExclusive(properties.slotStepMinutes());
            for (int i = booking.getStartSlot(); i < end && i < total; i++) {
                used[i] += booking.getPartySize();
            }
        }
        int[] remaining = new int[total];
        for (int i = 0; i < total; i++) {
            remaining[i] = properties.slotCapacity() - used[i];
        }
        return remaining;
    }

    /** Минимальная свободная вместимость среди всех получасовых слотов, занятых бронированием [start, start+span). */
    public int minRemainingForRange(int[] remainingPerSlot, int startSlot, int durationMinutes) {
        int span = slotsForDuration(durationMinutes);
        int min = Integer.MAX_VALUE;
        for (int i = startSlot; i < startSlot + span; i++) {
            min = Math.min(min, remainingPerSlot[i]);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public boolean fitsCapacity(int[] remainingPerSlot, int startSlot, int durationMinutes, int partySize) {
        return minRemainingForRange(remainingPerSlot, startSlot, durationMinutes) >= partySize;
    }

    public boolean overlaps(int startA, int durationMinutesA, int startB, int durationMinutesB) {
        int endA = startA + slotsForDuration(durationMinutesA);
        int endB = startB + slotsForDuration(durationMinutesB);
        return startA < endB && startB < endA;
    }

    public String formatSlotRange(int startSlot, int durationMinutes) {
        LocalTime start = slotStartTime(startSlot);
        LocalTime end = start.plusMinutes(durationMinutes);
        return format(start) + "-" + format(end);
    }

    public String formatDuration(int durationMinutes) {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("ч");
        }
        if (minutes > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(minutes).append("мин");
        }
        return sb.toString();
    }

    private String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
