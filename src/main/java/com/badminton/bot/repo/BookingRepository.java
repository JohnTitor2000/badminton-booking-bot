package com.badminton.bot.repo;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventIdAndStatusIn(Long eventId, List<BookingStatus> statuses);

    List<Booking> findByEventIdAndTelegramUserIdAndStatusIn(Long eventId, Long telegramUserId, List<BookingStatus> statuses);

    List<Booking> findByEventIdOrderByCreatedAtAsc(Long eventId);

    /**
     * Число подтверждённых записей на не отменённые события строго до {@code beforeDate}.
     * Возвращает пары [telegramUserId, count].
     */
    @Query("""
            select b.telegramUserId, count(b)
            from Booking b
            join b.event e
            where b.telegramUserId in :userIds
              and b.status = com.badminton.bot.domain.BookingStatus.CONFIRMED
              and e.status <> com.badminton.bot.domain.EventStatus.CANCELLED
              and e.eventDate < :beforeDate
            group by b.telegramUserId
            """)
    List<Object[]> countConfirmedVisitsBefore(@Param("userIds") Collection<Long> userIds,
                                              @Param("beforeDate") LocalDate beforeDate);
}
