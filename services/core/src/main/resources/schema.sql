PRAGMA foreign_keys = ON;

CREATE TABLE places (
  id INTEGER PRIMARY KEY, name TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('airport','station','hotel','restaurant','home')),
  city TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);

CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE trips (
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'active');

CREATE TABLE flights (
  id INTEGER PRIMARY KEY,
  from_place INTEGER NOT NULL REFERENCES places(id),
  to_place INTEGER NOT NULL REFERENCES places(id),
  dep TEXT NOT NULL, arr TEXT NOT NULL,
  seats_left INTEGER NOT NULL CHECK (seats_left >= 0), price INTEGER NOT NULL);

CREATE TABLE train_schedules (
  id INTEGER PRIMARY KEY,
  from_place INTEGER NOT NULL REFERENCES places(id),
  to_place INTEGER NOT NULL REFERENCES places(id),
  dep TEXT NOT NULL, arr TEXT NOT NULL,
  seats_left INTEGER NOT NULL CHECK (seats_left >= 0), price INTEGER NOT NULL);

CREATE TABLE rooms (
  id INTEGER PRIMARY KEY, hotel_place_id INTEGER NOT NULL REFERENCES places(id),
  room_type TEXT NOT NULL, price_per_night INTEGER NOT NULL);
CREATE TABLE room_inventory (
  room_id INTEGER NOT NULL REFERENCES rooms(id), date TEXT NOT NULL,
  rooms_left INTEGER NOT NULL CHECK (rooms_left >= 0),
  PRIMARY KEY (room_id, date));

CREATE TABLE table_slots (
  id INTEGER PRIMARY KEY, restaurant_place_id INTEGER NOT NULL REFERENCES places(id),
  slot_start TEXT NOT NULL,
  seats_left INTEGER NOT NULL CHECK (seats_left >= 0));

CREATE TABLE cab_fleet (
  id INTEGER PRIMARY KEY, city TEXT NOT NULL,
  base_fare INTEGER NOT NULL, per_km INTEGER NOT NULL);

CREATE TABLE bookings (
  id INTEGER PRIMARY KEY, trip_id INTEGER NOT NULL REFERENCES trips(id),
  type TEXT NOT NULL CHECK (type IN ('flight','train','hotel','restaurant','cab')),
  status TEXT NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed','cancelled')),
  start_time TEXT NOT NULL, end_time TEXT NOT NULL,
  origin_place_id INTEGER NOT NULL REFERENCES places(id),
  dest_place_id INTEGER NOT NULL REFERENCES places(id),
  inventory_ref TEXT, price INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);