package com.badminton.bot;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.service.SlotCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private SlotCalculator calculator;

    @BeforeEach
    void setUp() {
        BadmintonProperties properties = new BadmintonProperties(
                "Asia/Tbilisi",
                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,SATURDAY",
                "08:00",
                "11:00",
                30,
                2,
                8,
                2,
                "13:00");
        calculator = new SlotCalculator(properties);
    }

    @Test
    void durationOptionsCoverWholeWindow() {
        assertThat(calculator.durationOptionsMinutes()).containsExactly(30, 60, 90, 120, 150, 180);
    }

    @Test
    void totalSlotsIsSix() {
        assertThat(calculator.totalSlots()).isEqualTo(6);
    }

    @Test
    void possibleStartSlotsRespectSessionWindow() {
        assertThat(calculator.possibleStartSlots(180)).containsExactly(0);
        assertThat(calculator.possibleStartSlots(60)).containsExactly(0, 1, 2, 3, 4);
        assertThat(calculator.possibleStartSlots(30)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void remainingCapacityAccountsOnlyConfirmedBookings() {
        Booking confirmed = booking(0, 60, 3, BookingStatus.CONFIRMED);
        Booking waitlisted = booking(0, 60, 5, BookingStatus.WAITLISTED);

        int[] remaining = calculator.remainingCapacityPerSlot(List.of(confirmed, waitlisted));

        assertThat(remaining[0]).isEqualTo(5);
        assertThat(remaining[1]).isEqualTo(5);
        assertThat(remaining[2]).isEqualTo(8);
    }

    @Test
    void fitsCapacityChecksAllCoveredSlots() {
        Booking confirmed = booking(1, 30, 8, BookingStatus.CONFIRMED);
        int[] remaining = calculator.remainingCapacityPerSlot(List.of(confirmed));

        assertThat(calculator.fitsCapacity(remaining, 0, 60, 1)).isFalse();
        assertThat(calculator.fitsCapacity(remaining, 2, 60, 8)).isTrue();
    }

    @Test
    void overlapsDetectsIntersectingRanges() {
        assertThat(calculator.overlaps(0, 60, 1, 60)).isTrue();
        assertThat(calculator.overlaps(0, 60, 2, 60)).isFalse();
    }

    private Booking booking(int startSlot, int durationMinutes, int partySize, BookingStatus status) {
        return Booking.builder()
                .startSlot(startSlot)
                .durationMinutes(durationMinutes)
                .partySize(partySize)
                .status(status)
                .telegramUserId(1L)
                .displayName("Test")
                .createdAt(Instant.now())
                .build();
    }
}
