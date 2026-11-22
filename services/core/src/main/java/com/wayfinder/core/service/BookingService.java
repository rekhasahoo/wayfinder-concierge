package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.wayfinder.core.db.Database;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;

public final class BookingService {
    private final Path db;

    public BookingService(Path db) { this.db = db; }

    public Optional<Booking> find(long bookingId) throws SQLException {
        try (Connection c = Database.open(db)) {
            return Optional.ofNullable(load(c, bookingId));
        }
    }

    /** Cancels a booking and gives its inventory back, in one transaction. */
    public BookingResult cancel(long bookingId) throws SQLException {
        try (Connection c = Database.open(db)) {
            c.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookings SET status = 'cancelled' WHERE id = ? AND status = 'confirmed'")) {
                    ps.setLong(1, bookingId);
                    changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    c.rollback();
                    boolean exists = load(c, bookingId) != null;
                    return new BookingResult.Rejected(exists
                            ? BookingResult.Reason.ALREADY_CANCELLED
                            : BookingResult.Reason.NOT_FOUND);
                }
                Booking booking = load(c, bookingId);
                restoreInventory(c, booking);
                c.commit();
                return new BookingResult.Success(booking);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

        private static void restoreInventory(Connection c, Booking b) throws SQLException {
        String[] ref = b.inventoryRef().split(":");
        switch (ref[0]) {
            case "train", "flight" -> {
                TransportService.Mode mode = TransportService.Mode.fromType(ref[0]);
                update(c, "UPDATE " + mode.table + " SET seats_left = seats_left + 1 WHERE id = ?",
                        Long.parseLong(ref[1]));
            }
            case "table" -> update(c,
                    "UPDATE table_slots SET seats_left = seats_left + ? WHERE id = ?",
                    Integer.parseInt(ref[2]), Long.parseLong(ref[1]));
            default -> throw new IllegalStateException(
                    "Cancel not implemented yet for booking type: " + b.type());
        }
    }

    // ---- helpers shared with the domain services (same package only) ----

    static boolean tripExists(Connection c, long tripId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM trips WHERE id = ?")) {
            ps.setLong(1, tripId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static long insert(Connection c, long tripId, String type, String start, String end,
                       long origin, long dest, String inventoryRef, int price) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bookings(trip_id, type, status, start_time, end_time, "
                        + "origin_place_id, dest_place_id, inventory_ref, price) "
                        + "VALUES (?, ?, 'confirmed', ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, tripId);
            ps.setString(2, type);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setLong(5, origin);
            ps.setLong(6, dest);
            ps.setString(7, inventoryRef);
            ps.setInt(8, price);
            ps.executeUpdate();
        }
        return Database.lastInsertId(c);
    }

    static Booking load(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, trip_id, type, status, start_time, end_time, "
                        + "origin_place_id, dest_place_id, inventory_ref, price "
                        + "FROM bookings WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Booking(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8),
                        rs.getString(9), rs.getInt(10));
            }
        }
    }

    static void update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }
}