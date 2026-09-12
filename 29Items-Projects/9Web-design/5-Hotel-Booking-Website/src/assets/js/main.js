import { initBookingForm } from "../../components/BookingForm.js";
import { renderHotelResults } from "../../components/HotelResults.js";
import { initHotelMap } from "../../components/HotelMap.js";
import { initAdminDashboard } from "../../components/AdminDashboard.js";
import { checkAvailability, listHotels } from "../../services/bookingApi.js";

async function loadSeedHotels() {
  try {
    const today = new Date();
    const tomorrow = new Date(today.getTime() + 86_400_000);
    const following = new Date(today.getTime() + 172_800_000);

    return await checkAvailability({
      checkIn: tomorrow.toISOString().slice(0, 10),
      checkOut: following.toISOString().slice(0, 10),
      guests: 2,
    });
  } catch {
    try {
      const hotels = await listHotels();
      return hotels.map((hotel) => ({
        ...hotel,
        id: hotel.id,
        roomType: "standard",
        capacity: 2,
        pricePerNight: 0,
      }));
    } catch {
      const response = await fetch("./data/hotels.json");
      return response.json();
    }
  }
}

function initLibraries() {
  if (window.AOS) {
    window.AOS.init({ once: true, duration: 650 });
  }

  if (window.Swiper) {
    new window.Swiper(".hotel-swiper", {
      loop: true,
      pagination: {
        el: ".swiper-pagination",
        clickable: true,
      },
    });
  }
}

async function bootstrapApp() {
  initLibraries();

  try {
    const hotels = await loadSeedHotels();
    renderHotelResults(document.querySelector("#hotel-results"), hotels);
    initHotelMap("hotel-map", hotels);
  } catch (error) {
    renderHotelResults(document.querySelector("#hotel-results"), [], error);
  }

  initBookingForm({
    form: document.querySelector("#booking-form"),
    status: document.querySelector("#booking-status"),
    results: document.querySelector("#hotel-results"),
  });

  initAdminDashboard({
    root: document.querySelector("#admin-dashboard"),
    message: document.querySelector("#admin-status"),
  });
}

document.addEventListener("DOMContentLoaded", bootstrapApp);
