package com.badminton.bot.service;

import com.badminton.bot.repo.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Считает наигранные минуты и скилл игроков до указанной даты события.
 */
@Service
public class PlayerSkillService {

    private final BookingRepository bookingRepository;

    public PlayerSkillService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /** Наигранные минуты (confirmed, не cancelled, дата &lt; beforeDate). */
    public Map<Long, Long> minutesPlayedBefore(Collection<Long> telegramUserIds, LocalDate beforeDate) {
        Map<Long, Long> result = new HashMap<>();
        if (telegramUserIds == null || telegramUserIds.isEmpty() || beforeDate == null) {
            return result;
        }
        List<Long> ids = telegramUserIds.stream().distinct().toList();
        for (Object[] row : bookingRepository.sumConfirmedMinutesBefore(ids, beforeDate)) {
            Long userId = (Long) row[0];
            Number minutes = (Number) row[1];
            result.put(userId, minutes == null ? 0L : minutes.longValue());
        }
        return result;
    }

    public Map<Long, Double> skillsBefore(Collection<Long> telegramUserIds, LocalDate beforeDate) {
        Map<Long, Double> skills = new HashMap<>();
        for (Map.Entry<Long, Long> e : minutesPlayedBefore(telegramUserIds, beforeDate).entrySet()) {
            skills.put(e.getKey(), SlotSkillModel.playerSkillFromMinutes(e.getValue()));
        }
        // игроки без истории — 0
        if (telegramUserIds != null) {
            for (Long id : telegramUserIds) {
                skills.putIfAbsent(id, 0.0);
            }
        }
        return skills;
    }
}
