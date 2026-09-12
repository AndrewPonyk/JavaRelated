const repository = require("../repositories/inquiries.repository");
const toursRepository = require("../repositories/tours.repository");
const { notFound, validationFailed } = require("../errors");

const VALID_STATUSES = ["new", "contacted", "booked", "closed"];

async function listInquiries(filters) {
  return repository.listInquiries(filters);
}

async function getInquiry(id) {
  const inquiry = await repository.getInquiryById(id);

  if (!inquiry) {
    throw notFound("Inquiry");
  }

  return inquiry;
}

async function createInquiry(payload) {
  if (payload.tourId) {
    const tour = await toursRepository.getTourById(payload.tourId);

    if (!tour) {
      throw validationFailed([{ field: "tourId", message: "Selected tour does not exist." }]);
    }
  }

  return repository.createInquiry(payload);
}

async function updateInquiry(id, payload) {
  if (payload.status && !VALID_STATUSES.includes(payload.status)) {
    throw validationFailed([{ field: "status", message: `Status must be one of: ${VALID_STATUSES.join(", ")}.` }]);
  }

  if (payload.tourId) {
    const tour = await toursRepository.getTourById(payload.tourId);

    if (!tour) {
      throw validationFailed([{ field: "tourId", message: "Selected tour does not exist." }]);
    }
  }

  const inquiry = await repository.updateInquiry(id, payload);

  if (!inquiry) {
    throw notFound("Inquiry");
  }

  return inquiry;
}

async function deleteInquiry(id) {
  const deleted = await repository.deleteInquiry(id);

  if (!deleted) {
    throw notFound("Inquiry");
  }
}

module.exports = { createInquiry, deleteInquiry, getInquiry, listInquiries, updateInquiry, VALID_STATUSES };
