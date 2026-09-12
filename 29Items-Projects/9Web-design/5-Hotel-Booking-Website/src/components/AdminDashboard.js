import {
  createRoom,
  listBookings,
  listHotels,
  listRooms,
  updateBooking,
  updateRoom,
} from "../services/bookingApi.js";
import { escapeHtml } from "../utils/html.js";

function setMessage(element, message, variant = "muted") {
  element.className = `small text-${variant}`;
  element.textContent = message;
}

function roomRows(rooms) {
  return rooms
    .map(
      (room) => `
        <tr>
          <td>${escapeHtml(room.hotelName)}</td>
          <td>${escapeHtml(room.roomNumber)}</td>
          <td>${escapeHtml(room.roomType)}</td>
          <td>${Number(room.capacity || 0)}</td>
          <td>$${Number(room.pricePerNight || 0)}</td>
          <td>
            <select class="form-select form-select-sm" data-room-status="${escapeHtml(room.id)}">
              ${["available", "maintenance", "inactive"]
                .map(
                  (status) =>
                    `<option value="${status}" ${room.status === status ? "selected" : ""}>${status}</option>`,
                )
                .join("")}
            </select>
          </td>
        </tr>
      `,
    )
    .join("");
}

function bookingRows(bookings) {
  return bookings
    .map(
      (booking) => `
        <tr>
          <td>${escapeHtml(booking.confirmationCode)}</td>
          <td>${escapeHtml(booking.guestName)}</td>
          <td>${escapeHtml(booking.hotelName)}</td>
          <td>${escapeHtml(booking.checkIn)} to ${escapeHtml(booking.checkOut)}</td>
          <td>$${Number(booking.totalPrice || 0)}</td>
          <td>
            <select class="form-select form-select-sm" data-booking-status="${escapeHtml(booking.id)}">
              ${["pending_confirmation", "confirmed", "checked_in", "completed", "cancelled"]
                .map(
                  (status) =>
                    `<option value="${status}" ${booking.status === status ? "selected" : ""}>${status}</option>`,
                )
                .join("")}
            </select>
          </td>
        </tr>
      `,
    )
    .join("");
}

export function initAdminDashboard({ root, message }) {
  if (!root || !message) {
    return;
  }

  const tokenInput = root.querySelector("#staffToken");
  const roomTable = root.querySelector("#adminRoomRows");
  const bookingTable = root.querySelector("#adminBookingRows");
  const roomForm = root.querySelector("#roomForm");
  const hotelSelect = root.querySelector("#roomHotelId");

  async function refresh() {
    const token = tokenInput.value.trim();

    if (!token) {
      setMessage(message, "Enter staff token to load admin data.", "muted");
      return;
    }

    setMessage(message, "Loading admin data...", "muted");
    const [hotels, rooms, bookings] = await Promise.all([listHotels(), listRooms(), listBookings(token)]);

    hotelSelect.innerHTML = hotels
      .map((hotel) => `<option value="${escapeHtml(hotel.id)}">${escapeHtml(hotel.name)}</option>`)
      .join("");
    roomTable.innerHTML = roomRows(rooms);
    bookingTable.innerHTML = bookingRows(bookings);
    setMessage(message, "Admin data loaded.", "success");
  }

  root.querySelector("#loadAdminData").addEventListener("click", () => {
    refresh().catch((error) => setMessage(message, error.message, "danger"));
  });

  roomTable.addEventListener("change", async (event) => {
    const select = event.target.closest("[data-room-status]");

    if (!select) {
      return;
    }

    try {
      await updateRoom(select.dataset.roomStatus, { status: select.value }, tokenInput.value.trim());
      setMessage(message, "Room status updated.", "success");
    } catch (error) {
      setMessage(message, error.message, "danger");
    }
  });

  bookingTable.addEventListener("change", async (event) => {
    const select = event.target.closest("[data-booking-status]");

    if (!select) {
      return;
    }

    try {
      await updateBooking(select.dataset.bookingStatus, { status: select.value }, tokenInput.value.trim());
      setMessage(message, "Booking status updated.", "success");
    } catch (error) {
      setMessage(message, error.message, "danger");
    }
  });

  roomForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(roomForm);

    try {
      await createRoom(
        {
          hotelId: data.get("hotelId"),
          roomNumber: data.get("roomNumber"),
          roomType: data.get("roomType"),
          capacity: Number(data.get("capacity")),
          basePriceCents: Math.round(Number(data.get("basePrice")) * 100),
          status: "available",
          amenities: data
            .get("amenities")
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean),
        },
        tokenInput.value.trim(),
      );
      roomForm.reset();
      await refresh();
      setMessage(message, "Room created.", "success");
    } catch (error) {
      setMessage(message, error.message, "danger");
    }
  });
}
