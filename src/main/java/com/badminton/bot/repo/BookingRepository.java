package com.badminton.bot.repo;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventIdAndStatusIn(Long eventId, List<BookingStatus> statuses);

    List<Booking> findByEventIdAndTelegramUserIdAndStatusIn(Long eventId, Long telegramUserId, List<BookingStatus> statuses);

    List<Booking> findByEventIdOrderByCreatedAtAsc(Long eventId);
}
