package com.wayfinder.core.model;

public record Booking(
        long id, long tripId, String type, String status,
        String startTime, String endTime,
        long originPlaceId, long destPlaceId,
        String inventoryRef, int price) {}