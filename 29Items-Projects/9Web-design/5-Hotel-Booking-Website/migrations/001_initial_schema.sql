CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS hotels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(160) NOT NULL,
  slug VARCHAR(180) NOT NULL UNIQUE,
  city VARCHAR(120) NOT NULL,
  address TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  image_url TEXT NOT NULL DEFAULT '',
  amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
  room_type VARCHAR(40) NOT NULL CHECK (room_type IN ('standard', 'deluxe', 'suite')),
  room_number VARCHAR(30) NOT NULL,
  capacity INTEGER NOT NULL CHECK (capacity > 0 AND capacity <= 12),
  base_price_cents INTEGER NOT NULL CHECK (base_price_cents >= 0),
  status VARCHAR(30) NOT NULL DEFAULT 'available'
    CHECK (status IN ('available', 'maintenance', 'inactive')),
  image_url TEXT NOT NULL DEFAULT '',
  amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (hotel_id, room_number)
);

CREATE TABLE IF NOT EXISTS guests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name VARCHAR(160) NOT NULL,
  email VARCHAR(180) NOT NULL,
  phone VARCHAR(40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS bookings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  confirmation_code VARCHAR(40) NOT NULL UNIQUE,
  room_id UUID NOT NULL REFERENCES rooms(id),
  guest_id UUID NOT NULL REFERENCES guests(id),
  check_in DATE NOT NULL,
  check_out DATE NOT NULL,
  guests_count INTEGER NOT NULL CHECK (guests_count > 0),
  nightly_subtotal_cents INTEGER NOT NULL CHECK (nightly_subtotal_cents >= 0),
  taxes_cents INTEGER NOT NULL CHECK (taxes_cents >= 0),
  total_price_cents INTEGER NOT NULL CHECK (total_price_cents >= 0),
  status VARCHAR(30) NOT NULL DEFAULT 'pending_confirmation'
    CHECK (status IN ('pending_confirmation', 'confirmed', 'checked_in', 'completed', 'cancelled')),
  special_requests TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT valid_booking_dates CHECK (check_out > check_in)
);

CREATE TABLE IF NOT EXISTS staff_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(180) NOT NULL UNIQUE,
  role VARCHAR(40) NOT NULL DEFAULT 'receptionist'
    CHECK (role IN ('manager', 'receptionist', 'readonly')),
  display_name VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hotels_city ON hotels(city);
CREATE INDEX IF NOT EXISTS idx_rooms_hotel_id ON rooms(hotel_id);
CREATE INDEX IF NOT EXISTS idx_rooms_hotel_type_status ON rooms(hotel_id, room_type, status);
CREATE INDEX IF NOT EXISTS idx_bookings_room_dates ON bookings(room_id, check_in, check_out);
CREATE INDEX IF NOT EXISTS idx_bookings_guest_id ON bookings(guest_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON bookings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_guests_email ON guests(email);

INSERT INTO hotels (name, slug, city, address, description, latitude, longitude, image_url, amenities)
VALUES (
  'Central Park Hotel',
  'central-park-hotel',
  'New York',
  '15 West 59th Street, New York, NY',
  'A practical city hotel near Central Park with strong transit access.',
  40.7644,
  -73.9738,
  'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=900&q=80',
  '["Breakfast", "Wi-Fi", "Workspace"]'::jsonb
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO hotels (name, slug, city, address, description, latitude, longitude, image_url, amenities)
VALUES (
  'Harbor View Suites',
  'harbor-view-suites',
  'New York',
  '70 Pine Street, New York, NY',
  'Apartment-style suites for longer city stays and waterfront access.',
  40.7061,
  -74.0086,
  'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=900&q=80',
  '["Kitchenette", "Bay view", "Concierge"]'::jsonb
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO hotels (name, slug, city, address, description, latitude, longitude, image_url, amenities)
VALUES (
  'Midtown Business Inn',
  'midtown-business-inn',
  'New York',
  '125 West 45th Street, New York, NY',
  'Efficient business rooms close to Midtown offices and theaters.',
  40.7549,
  -73.984,
  'https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?auto=format&fit=crop&w=900&q=80',
  '["Gym", "Late checkout", "Meeting rooms"]'::jsonb
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO rooms (hotel_id, room_type, room_number, capacity, base_price_cents, status, image_url, amenities)
SELECT id, 'standard', '101', 2, 18000, 'available', image_url, '["Queen bed", "Desk", "Wi-Fi"]'::jsonb
FROM hotels WHERE slug = 'central-park-hotel'
ON CONFLICT (hotel_id, room_number) DO NOTHING;

INSERT INTO rooms (hotel_id, room_type, room_number, capacity, base_price_cents, status, image_url, amenities)
SELECT id, 'suite', '305', 4, 32000, 'available', image_url, '["King bed", "Sofa bed", "Kitchenette"]'::jsonb
FROM hotels WHERE slug = 'harbor-view-suites'
ON CONFLICT (hotel_id, room_number) DO NOTHING;

INSERT INTO rooms (hotel_id, room_type, room_number, capacity, base_price_cents, status, image_url, amenities)
SELECT id, 'deluxe', '210', 3, 24000, 'available', image_url, '["King bed", "Workspace", "City view"]'::jsonb
FROM hotels WHERE slug = 'midtown-business-inn'
ON CONFLICT (hotel_id, room_number) DO NOTHING;

INSERT INTO staff_users (email, role, display_name)
VALUES ('manager@staypilot.example', 'manager', 'Hotel Manager')
ON CONFLICT (email) DO NOTHING;
