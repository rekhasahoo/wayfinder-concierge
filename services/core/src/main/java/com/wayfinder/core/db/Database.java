package com.wayfinder.core.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private Database() {}

    public static Connection open(Path dbFile) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");   // SQLite enforces FKs per connection
        }
        return c;
    }

    public static void applySchema(Connection c) throws SQLException, IOException {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.isBlank()) st.execute(stmt);
            }
        }
    }
}