import { checkAvailability, createBooking } from "../services/bookingApi.js";
import { renderHotelResults } from "./HotelResults.js";

let selectedRoomId = "";

function getFormPayload(form) {
  const data = new FormData(form);

  return {
    checkIn: data.get("checkIn"),
    checkOut: data.get("checkOut"),
    guests: Number(data.get("guests")),
    roomType: data.get("roomType"),
    city: data.get("city"),
    guestName: data.get("guestName"),
    guestEmail: data.get("guestEmail"),
    guestPhone: data.get("guestPhone"),
    roomId: selectedRoomId || undefined,
    specialRequests: data.get("specialRequests"),
  };
}

function setStatus(element, message, variant = "muted") {
  element.className = `small mt-3 text-${variant}`;
  element.textContent = message;
}

function validateDates(payload) {
  if (!payload.checkIn || !payload.checkOut || payload.checkOut <= payload.checkIn) {
    return "Choose a valid check-in and check-out date.";
  }

  return "";
}

export function initBookingForm({ form, status, results }) {
  if (!form || !status || !results) {
    return;
  }

  results.addEventListener("click", (event) => {
    const button = event.target.closest("[data-room-id]");

    if (!button) {
      return;
    }

    selectedRoomId = button.dataset.roomId;
    setStatus(status, "Room selected. Complete guest details and submit booking.", "success");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (!form.checkValidity()) {
      form.classList.add("was-validated");
      setStatus(status, "Please complete the required booking fields.", "danger");
      return;
    }

    const payload = getFormPayload(form);
    const dateError = validateDates(payload);

    if (dateError) {
      setStatus(status, dateError, "danger");
      return;
    }

    setStatus(status, selectedRoomId ? "Creating booking..." : "Checking availability...", "muted");

    try {
      if (!selectedRoomId) {
        const availableRooms = await checkAvailability(payload);
        renderHotelResults(results, availableRooms);
        setStatus(status, `${availableRooms.length} available room(s) found. Select one to book.`, "success");
        return;
      }

      const booking = await createBooking(payload);
      selectedRoomId = "";
      form.reset();
      form.classList.remove("was-validated");
      setStatus(
        status,
        `Booking confirmed: ${booking.confirmationCode}. Total: $${booking.totalPrice}.`,
        "success",
      );
    } catch (error) {
      setStatus(status, error.message || "Booking request failed.", "danger");
    }
  });
}
