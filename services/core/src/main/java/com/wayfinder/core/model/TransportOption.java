package com.wayfinder.core.model;

public record TransportOption(long id, String origin, String destination,
                              String dep, String arr, int seatsLeft, int price) {}