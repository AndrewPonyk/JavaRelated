const { handleInquiryRequest } = require("../../src/api/routes/inquiries.route");

exports.handler = async (event) => handleInquiryRequest(event);
