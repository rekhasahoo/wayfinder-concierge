package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wayfinder.core.db.Database;
import com.wayfinder.core.db.SeedData;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;
import com.wayfinder.core.model.BookingResult.Reason;
import com.wayfinder.core.model.TransportOption;

class TransportServiceTest {
    @TempDir Path tmp;

    Path db;
    TransportService trains;
    BookingService bookings;
    long tripId;
    TransportOption train;   // first Chennai -> Bengaluru train on day 1

    @BeforeEach
    void setUp() throws Exception {
        db = tmp.resolve("t.db");
        SeedData.build(db);
        trains = new TransportService(db, TransportService.Mode.TRAIN);
        bookings = new BookingService(db);
        TripService trips = new TripService(db);
        tripId = trips.createTrip(trips.createUser("Test User"));
        train = trains.search("Chennai", "Bengaluru", "2026-11-22").get(0);
    }

    @Test
    void searchFindsTheRouteCaseInsensitively() throws Exception {
        List<TransportOption> found = trains.search("chennai", "BENGALURU", "2026-11-22");
        assertEquals(2, found.size());
        assertTrue(found.get(0).dep().compareTo(found.get(1).dep()) < 0, "earliest first");
        assertTrue(trains.search("Chennai", "Bengaluru", "2030-01-01").isEmpty());
    }

    @Test
    void bookingTakesOneSeatAndCreatesALeg() throws Exception {
        setSeats(train.id(), 5);
        BookingResult result = trains.book(tripId, train.id());

        Booking b = assertInstanceOf(BookingResult.Success.class, result).booking();
        assertEquals("train", b.type());
        assertEquals("confirmed", b.status());
        assertEquals(train.dep(), b.startTime());
        assertEquals(train.arr(), b.endTime());
        assertEquals("train:" + train.id(), b.inventoryRef());
        assertEquals(train.price(), b.price());
        assertEquals(4, seatsLeft(train.id()));
    }

    @Test
    void soldOutIsRejectedAndNothingChanges() throws Exception {
        setSeats(train.id(), 0);
        assertEquals(new BookingResult.Rejected(Reason.SOLD_OUT), trains.book(tripId, train.id()));
        assertEquals(0, seatsLeft(train.id()));
        assertEquals(0, bookingCount());
    }

    @Test
    void unknownTrainOrTripIsRejected() throws Exception {
        setSeats(train.id(), 5);
        assertEquals(new BookingResult.Rejected(Reason.NOT_FOUND), trains.book(tripId, 999_999));
        assertEquals(new BookingResult.Rejected(Reason.TRIP_NOT_FOUND), trains.book(999_999, train.id()));
        assertEquals(5, seatsLeft(train.id()));
        assertEquals(0, bookingCount());
    }

    @Test
    void cancelGivesTheSeatBackExactlyOnce() throws Exception {
        setSeats(train.id(), 5);
        long bookingId = assertInstanceOf(BookingResult.Success.class,
                trains.book(tripId, train.id())).booking().id();
        assertEquals(4, seatsLeft(train.id()));

        assertInstanceOf(BookingResult.Success.class, bookings.cancel(bookingId));
        assertEquals(5, seatsLeft(train.id()));
        assertEquals("cancelled", bookings.find(bookingId).orElseThrow().status());

        assertEquals(new BookingResult.Rejected(Reason.ALREADY_CANCELLED), bookings.cancel(bookingId));
        assertEquals(5, seatsLeft(train.id()), "a second cancel must not refund a second seat");
        assertEquals(new BookingResult.Rejected(Reason.NOT_FOUND), bookings.cancel(999_999));
    }

    @Test
    void concurrentBookingsNeverOversell() throws Exception {
        long id = train.id();
        setSeats(id, 1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookingResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();                 // all threads fire at the same moment
                return trains.book(tripId, id);
            }));
        }
        start.countDown();

        int success = 0, soldOut = 0;
        for (Future<BookingResult> f : futures) {
            BookingResult r = f.get(30, TimeUnit.SECONDS);
            if (r instanceof BookingResult.Success) {
                success++;
            } else if (r instanceof BookingResult.Rejected rej && rej.reason() == Reason.SOLD_OUT) {
                soldOut++;
            }
        }
        pool.shutdown();

        assertEquals(1, success);
        assertEquals(threads - 1, soldOut);
        assertEquals(0, seatsLeft(id));
        assertEquals(1, bookingCount());
    }

    // ---- helpers ----

    private void setSeats(long id, int seats) throws SQLException {
        try (Connection c = Database.open(db);
             PreparedStatement ps = c.prepareStatement("UPDATE train_schedules SET seats_left = ? WHERE id = ?")) {
            ps.setInt(1, seats);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private int seatsLeft(long id) throws SQLException {
        return queryInt("SELECT seats_left FROM train_schedules WHERE id = ?", id);
    }

    private int bookingCount() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM bookings");
    }

    private int queryInt(String sql, Object... args) throws SQLException {
        try (Connection c = Database.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}