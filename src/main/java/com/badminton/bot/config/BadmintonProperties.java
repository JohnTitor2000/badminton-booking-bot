package com.badminton.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "badminton")
public record BadmintonProperties(
        String timezone,
        String trainingDays,
        String sessionStart,
        String sessionEnd,
        int slotStepMinutes,
        int courtsCount,
        int slotCapacity,
        int registrationLeadDays,
        String publishTime
) {

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public LocalTime sessionStartTime() {
        return LocalTime.parse(sessionStart);
    }

    public LocalTime sessionEndTime() {
        return LocalTime.parse(sessionEnd);
    }

    public LocalTime publishAtTime() {
        return LocalTime.parse(publishTime);
    }

    public List<DayOfWeek> trainingDaysList() {
        return List.of(trainingDays.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> DayOfWeek.valueOf(s.toUpperCase()))
                .toList();
    }

    /** Количество получасовых слотов в тренировочном окне (например, 6 для 8:00-11:00). */
    public int slotsCount() {
        long minutes = java.time.Duration.between(sessionStartTime(), sessionEndTime()).toMinutes();
        return (int) (minutes / slotStepMinutes);
    }
}
