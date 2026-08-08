package com.badminton.bot.service;

public enum BookingOutcome {
    CONFIRMED,
    WAITLISTED,
    EVENT_NOT_OPEN,
    PARTY_TOO_BIG,
    OVERLAPS_OWN_BOOKING
}
