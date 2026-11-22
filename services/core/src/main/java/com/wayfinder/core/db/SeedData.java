package com.wayfinder.core.db;

import com.wayfinder.core.geo.Geo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SeedData {
    static final long SEED = 42;
    static final LocalDateTime START = LocalDateTime.of(2026, 11, 22, 0, 0); // fixed on purpose
    static final int DAYS = 3;
    static final int INVENTORY_DAYS = DAYS + 5;
    static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    record City(String name, double lat, double lon) {}
    record Hub(long airport, long station, long home) {}

    static final List<City> CITIES = List.of(
            new City("Chennai", 13.0827, 80.2707), new City("Bengaluru", 12.9716, 77.5946),
            new City("Mumbai", 19.0760, 72.8777), new City("Delhi", 28.6139, 77.2090),
            new City("Hyderabad", 17.3850, 78.4867), new City("Kolkata", 22.5726, 88.3639));
    static final List<String> HOTELS =
            List.of("Grand Palm", "Riverside Inn", "Central Residency", "Skyline Suites");
    static final List<String> RESTAURANTS = List.of("Spice Route", "The Curry Leaf", "Tandoor House");

    private SeedData() {}

    public static void build(Path dbFile) throws Exception {
        Files.createDirectories(dbFile.toAbsolutePath().getParent());
        Files.deleteIfExists(dbFile);
        Random rng = new Random(SEED);

        try (Connection c = Database.open(dbFile)) {
            Database.applySchema(c);
            c.setAutoCommit(false);

            Map<Long, double[]> xy = new HashMap<>();
            Map<String, Hub> hubs = new HashMap<>();

            for (City city : CITIES) {
                long airport = place(c, xy, city.name() + " Airport", "airport", city.name(),
                        city.lat() + 0.2, city.lon() + 0.1);
                long station = place(c, xy, city.name() + " Central", "station", city.name(),
                        city.lat(), city.lon());
                long home = place(c, xy, city.name() + " Home", "home", city.name(),
                        city.lat() - 0.03, city.lon() + 0.02);
                hubs.put(city.name(), new Hub(airport, station, home));

                for (String name : HOTELS) {
                    long hotel = place(c, xy, name + " " + city.name(), "hotel", city.name(),
                            city.lat() + jitter(rng), city.lon() + jitter(rng));
                    seedRooms(c, rng, hotel, "standard", 20, 35);
                    seedRooms(c, rng, hotel, "deluxe", 40, 65);
                }
                for (String name : RESTAURANTS) {
                    long rest = place(c, xy, name + " " + city.name(), "restaurant", city.name(),
                            city.lat() + jitter(rng), city.lon() + jitter(rng));
                    for (int d = 0; d < INVENTORY_DAYS; d++) {
                        for (int hour : new int[]{13, 20}) {
                            insert(c, "INSERT INTO table_slots(restaurant_place_id, slot_start, seats_left) VALUES (?,?,?)",
                                    rest, START.plusDays(d).plusHours(hour).format(ISO_MIN), rng.nextInt(13));
                        }
                    }
                }
                insert(c, "INSERT INTO cab_fleet(city, base_fare, per_km) VALUES (?,?,?)",
                        city.name(), 50, 14);
            }

            for (City a : CITIES) {
                for (City b : CITIES) {
                    if (a == b) continue;
                    Hub ha = hubs.get(a.name()), hb = hubs.get(b.name());
                    double[] pa = xy.get(ha.airport()), pb = xy.get(hb.airport());
                    double km = Geo.haversineKm(pa[0], pa[1], pb[0], pb[1]);
                    for (int d = 0; d < DAYS; d++) {
                        LocalDateTime day = START.plusDays(d);
                        for (int hour : new int[]{7, 18}) {          // two flights a day
                            LocalDateTime dep = day.plusHours(hour).plusMinutes(pick(rng, 0, 15, 30, 45));
                            LocalDateTime arr = dep.plusMinutes((long) (km / 700 * 60) + 45);
                            insert(c, "INSERT INTO flights(from_place,to_place,dep,arr,seats_left,price) VALUES (?,?,?,?,?,?)",
                                    ha.airport(), hb.airport(), dep.format(ISO_MIN), arr.format(ISO_MIN),
                                    rng.nextInt(41), (int) (2500 + km * 4.5));
                        }
                        for (int hour : new int[]{6, 21}) {          // two trains a day
                            LocalDateTime dep = day.plusHours(hour).plusMinutes(pick(rng, 0, 10, 20, 40));
                            LocalDateTime arr = dep.plusMinutes((long) (km * 1.3 / 55 * 60));
                            insert(c, "INSERT INTO train_schedules(from_place,to_place,dep,arr,seats_left,price) VALUES (?,?,?,?,?,?)",
                                    ha.station(), hb.station(), dep.format(ISO_MIN), arr.format(ISO_MIN),
                                    rng.nextInt(81), (int) (300 + km * 1.2));
                        }
                    }
                }
            }
            c.commit();
        }
    }

    private static void seedRooms(Connection c, Random rng, long hotel, String type, int lo, int hi)
            throws SQLException {
        long room = insert(c, "INSERT INTO rooms(hotel_place_id, room_type, price_per_night) VALUES (?,?,?)",
                hotel, type, (lo + rng.nextInt(hi - lo + 1)) * 100);
        for (int d = 0; d < INVENTORY_DAYS; d++) {
            insert(c, "INSERT INTO room_inventory(room_id, date, rooms_left) VALUES (?,?,?)",
                    room, START.plusDays(d).toLocalDate().toString(), rng.nextInt(7));
        }
    }

    private static long place(Connection c, Map<Long, double[]> xy, String name, String kind,
                              String city, double lat, double lon) throws SQLException {
        long id = insert(c, "INSERT INTO places(name,kind,city,lat,lon) VALUES (?,?,?,?,?)",
                name, kind, city, lat, lon);
        xy.put(id, new double[]{lat, lon});
        return id;
    }

    private static double jitter(Random rng) { return (rng.nextDouble() * 2 - 1) * 0.05; }

    private static int pick(Random rng, int... options) { return options[rng.nextInt(options.length)]; }

    /** Inserts a row and returns its generated id. */
    private static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public static void main(String[] args) throws Exception {
        Path db = Path.of(args.length > 0 ? args[0] : "../data/concierge.db");
        build(db);
        try (Connection c = Database.open(db); Statement st = c.createStatement()) {
            for (String t : List.of("places", "flights", "train_schedules", "rooms",
                    "room_inventory", "table_slots")) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    System.out.println(t + " " + rs.getInt(1));
                }
            }
        }
    }
}