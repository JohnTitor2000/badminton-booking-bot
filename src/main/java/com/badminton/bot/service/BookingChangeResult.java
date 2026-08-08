package com.badminton.bot.service;

import com.badminton.bot.domain.Booking;

import java.util.List;

public record BookingChangeResult(BookingOutcome outcome, Booking booking, List<Booking> promoted) {

    public static BookingChangeResult error(BookingOutcome outcome) {
        return new BookingChangeResult(outcome, null, List.of());
    }
}
