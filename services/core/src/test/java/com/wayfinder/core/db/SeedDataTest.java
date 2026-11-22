package com.wayfinder.core.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeedDataTest {
    @TempDir Path tmp;

    private static String dump(Path db) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Connection c = Database.open(db); Statement st = c.createStatement()) {
            for (String t : List.of("places", "flights", "train_schedules", "rooms",
                    "room_inventory", "table_slots", "cab_fleet")) {
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + t + " ORDER BY rowid")) {
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) sb.append(rs.getString(i)).append('|');
                        sb.append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void seedIsDeterministic() throws Exception {
        SeedData.build(tmp.resolve("a.db"));
        SeedData.build(tmp.resolve("b.db"));
        assertEquals(dump(tmp.resolve("a.db")), dump(tmp.resolve("b.db")));
    }

    @Test
    void countsAndConstraints() throws Exception {
        Path db = tmp.resolve("c.db");
        SeedData.build(db);
        try (Connection c = Database.open(db); Statement st = c.createStatement()) {
            assertEquals(24, count(st, "SELECT COUNT(*) FROM places WHERE kind='hotel'"));
            assertEquals(18, count(st, "SELECT COUNT(*) FROM places WHERE kind='restaurant'"));
            assertThrows(SQLException.class,
                    () -> st.executeUpdate("UPDATE flights SET seats_left = -1 WHERE id = 1"));
        }
    }
}