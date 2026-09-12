CREATE TABLE IF NOT EXISTS reservations (
  id UUID PRIMARY KEY,
  customer_name TEXT NOT NULL,
  customer_email TEXT NOT NULL,
  reservation_date DATE NOT NULL,
  party_size INTEGER NOT NULL CHECK (party_size BETWEEN 1 AND 12),
  notes TEXT,
  status TEXT NOT NULL DEFAULT 'requested' CHECK (status IN ('requested', 'confirmed', 'declined', 'cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reservations_reservation_date ON reservations (reservation_date);
CREATE INDEX IF NOT EXISTS idx_reservations_status ON reservations (status);
