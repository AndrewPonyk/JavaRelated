const { handleDestinationRequest } = require("../../src/api/routes/destinations.route");

exports.handler = async (event) => handleDestinationRequest(event);
