package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.wayfinder.core.db.Database;

public final class TripService {
    private final Path db;

    public TripService(Path db) { this.db = db; }

    public long createUser(String name) throws SQLException {
        try (Connection c = Database.open(db);
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            return Database.lastInsertId(c);
        }
    }

    public long createTrip(long userId) throws SQLException {
        try (Connection c = Database.open(db);
             PreparedStatement ps = c.prepareStatement("INSERT INTO trips(user_id) VALUES (?)")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            return Database.lastInsertId(c);
        }
    }
}