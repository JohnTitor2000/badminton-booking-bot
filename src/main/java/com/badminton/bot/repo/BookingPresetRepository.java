package com.badminton.bot.repo;

import com.badminton.bot.domain.BookingPreset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingPresetRepository extends JpaRepository<BookingPreset, Long> {
}
