package com.wayfinder.core.service;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wayfinder.core.db.SeedData;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;
import com.wayfinder.core.model.BookingResult.Reason;
import com.wayfinder.core.model.TableOption;

class RestaurantServiceTest {
    // Slot 1 is the first slot the seeder creates: Chennai, 2026-11-22 13:00.
    static final long SLOT = 1;

    @TempDir Path tmp;

    Path db;
    RestaurantService restaurants;
    BookingService bookings;
    long tripId;

    @BeforeEach
    void setUp() throws Exception {
        db = tmp.resolve("r.db");
        SeedData.build(db);
        restaurants = new RestaurantService(db);
        bookings = new BookingService(db);
        TripService trips = new TripService(db);
        tripId = trips.createTrip(trips.createUser("Test User"));
    }

    @Test
    void searchHidesSlotsThatCannotFitTheParty() throws Exception {
        setSeats(2);
        assertTrue(ids(restaurants.search("Chennai", "2026-11-22", 2)).contains(SLOT));
        assertFalse(ids(restaurants.search("Chennai", "2026-11-22", 3)).contains(SLOT));
    }

    @Test
    void bookingTakesThePartySizeAndCancelGivesItBack() throws Exception {
        setSeats(5);
        Booking b = assertInstanceOf(BookingResult.Success.class,
                restaurants.book(tripId, SLOT, 3)).booking();

        assertEquals("restaurant", b.type());
        assertEquals("table:1:3", b.inventoryRef());
        assertEquals(b.originPlaceId(), b.destPlaceId());
        assertEquals("2026-11-22T13:00", b.startTime());
        assertEquals("2026-11-22T14:30", b.endTime());
        assertEquals(2, seats());

        assertInstanceOf(BookingResult.Success.class, bookings.cancel(b.id()));
        assertEquals(5, seats());
    }

    @Test
    void notEnoughSeatsIsRejectedAndNothingChanges() throws Exception {
        setSeats(2);
        assertEquals(new BookingResult.Rejected(Reason.SOLD_OUT), restaurants.book(tripId, SLOT, 3));
        assertEquals(2, seats());
        assertEquals(0, TestDb.queryInt(db, "SELECT COUNT(*) FROM bookings"));
    }

    @Test
    void invalidPartySizeIsAProgrammerError() {
        assertThrows(IllegalArgumentException.class, () -> restaurants.book(tripId, SLOT, 0));
    }

    private void setSeats(int n) throws Exception {
        TestDb.exec(db, "UPDATE table_slots SET seats_left = ? WHERE id = ?", n, SLOT);
    }

    private int seats() throws Exception {
        return TestDb.queryInt(db, "SELECT seats_left FROM table_slots WHERE id = ?", SLOT);
    }

    private static List<Long> ids(List<TableOption> options) {
        return options.stream().map(TableOption::id).toList();
    }
}