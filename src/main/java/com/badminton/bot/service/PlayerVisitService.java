package com.badminton.bot.service;

import com.badminton.bot.repo.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сколько раз игрок уже был на тренировках (подтверждённые записи на прошедшие/не отменённые дни).
 */
@Service
public class PlayerVisitService {

    private final BookingRepository bookingRepository;

    public PlayerVisitService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * @param beforeDate дата текущего события — визиты в этот день и позже не считаются
     */
    public Map<Long, Integer> visitCountsBefore(Collection<Long> telegramUserIds, LocalDate beforeDate) {
        Map<Long, Integer> result = new HashMap<>();
        if (telegramUserIds == null || telegramUserIds.isEmpty() || beforeDate == null) {
            return result;
        }
        List<Long> ids = telegramUserIds.stream().distinct().toList();
        for (Object[] row : bookingRepository.countConfirmedVisitsBefore(ids, beforeDate)) {
            Long userId = (Long) row[0];
            long count = (Long) row[1];
            result.put(userId, (int) count);
        }
        return result;
    }

    public int visitCountBefore(Long telegramUserId, LocalDate beforeDate) {
        if (telegramUserId == null) {
            return 0;
        }
        return visitCountsBefore(List.of(telegramUserId), beforeDate).getOrDefault(telegramUserId, 0);
    }
}
