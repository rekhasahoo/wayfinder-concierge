package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.wayfinder.core.db.Database;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;
import com.wayfinder.core.model.TableOption;

public final class RestaurantService {
    private static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int SITTING_MINUTES = 90;

    private final Path db;

    public RestaurantService(Path db) { this.db = db; }

    /** Slots in a city on a date (yyyy-MM-dd) that can seat the whole party. */
    public List<TableOption> search(String city, String date, int partySize) throws SQLException {
        String sql = """
                SELECT s.id, p.name, s.slot_start, s.seats_left
                FROM table_slots s
                JOIN places p ON p.id = s.restaurant_place_id
                WHERE lower(p.city) = lower(?) AND substr(s.slot_start, 1, 10) = ?
                  AND s.seats_left >= ?
                ORDER BY s.slot_start, p.name
                """;
        try (Connection c = Database.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, city);
            ps.setString(2, date);
            ps.setInt(3, partySize);
            try (ResultSet rs = ps.executeQuery()) {
                List<TableOption> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TableOption(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                }
                return out;
            }
        }
    }

    /** Books {@code partySize} seats. Not enough seats is reported as SOLD_OUT. */
    public BookingResult book(long tripId, long slotId, int partySize) throws SQLException {
        if (partySize < 1) {
            throw new IllegalArgumentException("partySize must be at least 1");
        }
        try (Connection c = Database.open(db)) {
            if (!BookingService.tripExists(c, tripId)) {
                return new BookingResult.Rejected(BookingResult.Reason.TRIP_NOT_FOUND);
            }
            c.setAutoCommit(false);
            try {
                // Same atomic idea, but takes N seats and only if N are free.
                int taken;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE table_slots SET seats_left = seats_left - ? "
                                + "WHERE id = ? AND seats_left >= ?")) {
                    ps.setInt(1, partySize);
                    ps.setLong(2, slotId);
                    ps.setInt(3, partySize);
                    taken = ps.executeUpdate();
                }
                if (taken == 0) {
                    c.rollback();
                    return new BookingResult.Rejected(exists(c, slotId)
                            ? BookingResult.Reason.SOLD_OUT
                            : BookingResult.Reason.NOT_FOUND);
                }
                long bookingId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT restaurant_place_id, slot_start FROM table_slots WHERE id = ?")) {
                    ps.setLong(1, slotId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        long place = rs.getLong(1);
                        String start = rs.getString(2);
                        String end = LocalDateTime.parse(start).plusMinutes(SITTING_MINUTES).format(ISO_MIN);
                        // a meal doesn't move you: origin and destination are the same place
                        bookingId = BookingService.insert(c, tripId, "restaurant", start, end,
                                place, place, "table:" + slotId + ":" + partySize, 0);
                    }
                }
                Booking booking = BookingService.load(c, bookingId);
                c.commit();
                return new BookingResult.Success(booking);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static boolean exists(Connection c, long slotId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM table_slots WHERE id = ?")) {
            ps.setLong(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}