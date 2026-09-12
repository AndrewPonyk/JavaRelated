function centsToDollars(cents) {
  return Number((cents / 100).toFixed(2));
}

function normalizeJsonArray(value) {
  if (Array.isArray(value)) {
    return value;
  }

  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  return [];
}

function formatDateOnly(value) {
  if (value instanceof Date) {
    return value.toISOString().slice(0, 10);
  }

  return value;
}

function hotel(row) {
  return {
    id: row.id,
    name: row.name,
    slug: row.slug,
    city: row.city,
    address: row.address,
    description: row.description,
    latitude: row.latitude === null ? null : Number(row.latitude),
    longitude: row.longitude === null ? null : Number(row.longitude),
    coordinates:
      row.latitude === null || row.longitude === null ? null : [Number(row.latitude), Number(row.longitude)],
    imageUrl: row.image_url,
    amenities: normalizeJsonArray(row.amenities),
    active: row.active,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function room(row) {
  return {
    id: row.id,
    hotelId: row.hotel_id,
    hotelName: row.hotel_name,
    city: row.city,
    roomType: row.room_type,
    roomNumber: row.room_number,
    capacity: row.capacity,
    basePriceCents: row.base_price_cents,
    pricePerNight: centsToDollars(row.base_price_cents),
    status: row.status,
    imageUrl: row.image_url || row.hotel_image_url || "",
    amenities: normalizeJsonArray(row.amenities),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function guest(row) {
  return {
    id: row.id,
    fullName: row.full_name,
    email: row.email,
    phone: row.phone,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function booking(row) {
  return {
    id: row.id,
    confirmationCode: row.confirmation_code,
    roomId: row.room_id,
    guestId: row.guest_id,
    checkIn: formatDateOnly(row.check_in),
    checkOut: formatDateOnly(row.check_out),
    guestsCount: row.guests_count,
    nightlySubtotalCents: row.nightly_subtotal_cents,
    taxesCents: row.taxes_cents,
    totalPriceCents: row.total_price_cents,
    totalPrice: centsToDollars(row.total_price_cents),
    status: row.status,
    specialRequests: row.special_requests,
    guestEmail: row.guest_email,
    guestName: row.guest_name,
    hotelName: row.hotel_name,
    roomNumber: row.room_number,
    roomType: row.room_type,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function staffUser(row) {
  return {
    id: row.id,
    email: row.email,
    role: row.role,
    displayName: row.display_name,
    active: row.active,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

module.exports = {
  booking,
  centsToDollars,
  guest,
  hotel,
  room,
  staffUser,
};
