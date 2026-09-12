async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const body = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(body.error?.message || "Request failed.");
  }

  return body.data;
}

function queryString(params) {
  const search = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, value);
    }
  });

  return search.toString();
}

function staffHeaders(token) {
  return token ? { "x-staff-token": token, "x-staff-role": "manager" } : {};
}

export function checkAvailability(params) {
  return request(`/api/availability?${queryString(params)}`);
}

export function createBooking(payload) {
  return request("/api/bookings", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function listHotels(params = {}) {
  return request(`/api/hotels?${queryString(params)}`);
}

export function listRooms(params = {}) {
  return request(`/api/rooms?${queryString(params)}`);
}

export function createRoom(payload, token) {
  return request("/api/rooms", {
    method: "POST",
    headers: staffHeaders(token),
    body: JSON.stringify(payload),
  });
}

export function updateRoom(id, payload, token) {
  return request(`/api/rooms?${queryString({ id })}`, {
    method: "PATCH",
    headers: staffHeaders(token),
    body: JSON.stringify(payload),
  });
}

export function listBookings(token) {
  return request("/api/bookings", {
    headers: staffHeaders(token),
  });
}

export function updateBooking(id, payload, token) {
  return request(`/api/bookings?${queryString({ id })}`, {
    method: "PATCH",
    headers: staffHeaders(token),
    body: JSON.stringify(payload),
  });
}
