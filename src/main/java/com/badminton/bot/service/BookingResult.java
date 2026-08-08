package com.badminton.bot.service;

import com.badminton.bot.domain.Booking;

public record BookingResult(BookingOutcome outcome, Booking booking) {

    public static BookingResult of(BookingOutcome outcome, Booking booking) {
        return new BookingResult(outcome, booking);
    }

    public static BookingResult error(BookingOutcome outcome) {
        return new BookingResult(outcome, null);
    }
}
