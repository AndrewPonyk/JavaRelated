const { handleTourRequest } = require("../../src/api/routes/tours.route");

exports.handler = async (event) => handleTourRequest(event);
