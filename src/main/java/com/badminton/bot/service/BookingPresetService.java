package com.badminton.bot.service;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingPreset;
import com.badminton.bot.repo.BookingPresetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class BookingPresetService {

    private final BookingPresetRepository repository;
    private final SlotCalculator slotCalculator;

    public BookingPresetService(BookingPresetRepository repository, SlotCalculator slotCalculator) {
        this.repository = repository;
        this.slotCalculator = slotCalculator;
    }

    public Optional<BookingPreset> find(Long telegramUserId) {
        return repository.findById(telegramUserId);
    }

    /** Пресет, если он ещё совместим с текущим расписанием сессии. */
    public Optional<BookingPreset> findValid(Long telegramUserId) {
        return find(telegramUserId).filter(this::isValidForSession);
    }

    public boolean isValidForSession(BookingPreset preset) {
        if (preset.getPartySize() < 1) {
            return false;
        }
        return slotCalculator.possibleStartSlots(preset.getDurationMinutes()).contains(preset.getStartSlot());
    }

    @Transactional
    public BookingPreset saveFromBooking(Booking booking) {
        BookingPreset preset = repository.findById(booking.getTelegramUserId())
                .orElseGet(() -> BookingPreset.builder()
                        .telegramUserId(booking.getTelegramUserId())
                        .build());
        preset.setStartSlot(booking.getStartSlot());
        preset.setDurationMinutes(booking.getDurationMinutes());
        preset.setPartySize(booking.getPartySize());
        preset.setUpdatedAt(Instant.now());
        preset = repository.save(preset);
        log.info("Пресет сохранён для user={}: slot={} duration={} size={}",
                booking.getTelegramUserId(), preset.getStartSlot(), preset.getDurationMinutes(), preset.getPartySize());
        return preset;
    }

    @Transactional
    public void delete(Long telegramUserId) {
        repository.deleteById(telegramUserId);
    }
}
