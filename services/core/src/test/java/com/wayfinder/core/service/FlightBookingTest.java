package com.wayfinder.core.service;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wayfinder.core.db.SeedData;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;
import com.wayfinder.core.model.TransportOption;

class FlightBookingTest {
    @TempDir Path tmp;

    Path db;
    TransportService flights;
    BookingService bookings;
    long tripId;

    @BeforeEach
    void setUp() throws Exception {
        db = tmp.resolve("f.db");
        SeedData.build(db);
        flights = new TransportService(db, TransportService.Mode.FLIGHT);
        bookings = new BookingService(db);
        TripService trips = new TripService(db);
        tripId = trips.createTrip(trips.createUser("Test User"));
    }

    @Test
    void searchReturnsAirportsAndBothDailyFlights() throws Exception {
        List<TransportOption> found = flights.search("Chennai", "Delhi", "2026-11-22");
        assertEquals(2, found.size());
        assertTrue(found.get(0).origin().contains("Airport"));
        assertTrue(found.get(0).destination().contains("Airport"));
    }

    @Test
    void bookAndCancelRestoreTheSeat() throws Exception {
        TransportOption f = flights.search("Chennai", "Delhi", "2026-11-22").get(0);
        TestDb.exec(db, "UPDATE flights SET seats_left = 3 WHERE id = ?", f.id());

        Booking b = assertInstanceOf(BookingResult.Success.class,
                flights.book(tripId, f.id())).booking();
        assertEquals("flight", b.type());
        assertEquals("flight:" + f.id(), b.inventoryRef());
        assertEquals(2, TestDb.queryInt(db, "SELECT seats_left FROM flights WHERE id = ?", f.id()));

        assertInstanceOf(BookingResult.Success.class, bookings.cancel(b.id()));
        assertEquals(3, TestDb.queryInt(db, "SELECT seats_left FROM flights WHERE id = ?", f.id()));
    }
}