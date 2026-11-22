package com.wayfinder.core.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.wayfinder.core.db.Database;

final class TestDb {
    private TestDb() {}

    static int queryInt(Path db, String sql, Object... args) throws SQLException {
        try (Connection c = Database.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static void exec(Path db, String sql, Object... args) throws SQLException {
        try (Connection c = Database.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }
}