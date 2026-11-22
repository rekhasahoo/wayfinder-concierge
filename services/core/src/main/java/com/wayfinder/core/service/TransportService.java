package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.wayfinder.core.db.Database;
import com.wayfinder.core.model.Booking;
import com.wayfinder.core.model.BookingResult;
import com.wayfinder.core.model.TransportOption;

public final class TransportService {

    /**
     * The only values that ever reach the SQL text as identifiers. A closed enum makes
     * that safe. Never build SQL text from anything a user or model typed.
     */
    public enum Mode {
        TRAIN("train", "train_schedules"),
        FLIGHT("flight", "flights");

        final String type;    // stored in bookings.type and as the inventory_ref prefix
        final String table;   // inventory table

        Mode(String type, String table) {
            this.type = type;
            this.table = table;
        }

        static Mode fromType(String type) {
            for (Mode m : values()) {
                if (m.type.equals(type)) return m;
            }
            throw new IllegalArgumentException("Unknown transport type: " + type);
        }
    }

    private final Path db;
    private final Mode mode;

    public TransportService(Path db, Mode mode) {
        this.db = db;
        this.mode = mode;
    }

    /** Services between two cities on a date (yyyy-MM-dd), earliest first. Sold-out ones included. */
    public List<TransportOption> search(String originCity, String destCity, String date) throws SQLException {
        String sql = """
                SELECT t.id, po.name, pd.name, t.dep, t.arr, t.seats_left, t.price
                FROM %s t
                JOIN places po ON po.id = t.from_place
                JOIN places pd ON pd.id = t.to_place
                WHERE lower(po.city) = lower(?) AND lower(pd.city) = lower(?)
                  AND substr(t.dep, 1, 10) = ?
                ORDER BY t.dep
                """.formatted(mode.table);
        try (Connection c = Database.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, originCity);
            ps.setString(2, destCity);
            ps.setString(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                List<TransportOption> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TransportOption(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7)));
                }
                return out;
            }
        }
    }

    public BookingResult book(long tripId, long serviceId) throws SQLException {
        try (Connection c = Database.open(db)) {
            // Read-only pre-check while still in autocommit, so no lock is held afterwards.
            if (!BookingService.tripExists(c, tripId)) {
                return new BookingResult.Rejected(BookingResult.Reason.TRIP_NOT_FOUND);
            }
            c.setAutoCommit(false);
            try {
                // 1. Take the seat: ONE statement that checks and decrements.
                int taken;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + mode.table + " SET seats_left = seats_left - 1 "
                                + "WHERE id = ? AND seats_left > 0")) {
                    ps.setLong(1, serviceId);
                    taken = ps.executeUpdate();
                }
                if (taken == 0) {
                    c.rollback();
                    return new BookingResult.Rejected(exists(c, serviceId)
                            ? BookingResult.Reason.SOLD_OUT
                            : BookingResult.Reason.NOT_FOUND);
                }
                // 2. Record the booking as a leg, in the same transaction.
                long bookingId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT dep, arr, from_place, to_place, price FROM " + mode.table + " WHERE id = ?")) {
                    ps.setLong(1, serviceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        bookingId = BookingService.insert(c, tripId, mode.type,
                                rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                                mode.type + ":" + serviceId, rs.getInt(5));
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

    private boolean exists(Connection c, long serviceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + mode.table + " WHERE id = ?")) {
            ps.setLong(1, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}