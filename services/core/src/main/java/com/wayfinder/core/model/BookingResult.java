package com.wayfinder.core.model;

public sealed interface BookingResult {
    enum Reason { SOLD_OUT, NOT_FOUND, TRIP_NOT_FOUND, ALREADY_CANCELLED }

    record Success(Booking booking) implements BookingResult {}

    record Rejected(Reason reason) implements BookingResult {}
}