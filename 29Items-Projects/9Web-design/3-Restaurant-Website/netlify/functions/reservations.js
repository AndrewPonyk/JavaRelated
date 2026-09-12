import { createReservationController } from '../../src/backend/controllers/reservationController.js';

const jsonHeaders = {
  'Content-Type': 'application/json',
  'Cache-Control': 'no-store'
};

let controller;

export async function handler(event) {
  if (['POST', 'PUT'].includes(event.httpMethod) && !isJson(event.headers)) {
    return json(415, {
      ok: false,
      error: 'Content-Type must be application/json.',
      code: 'unsupported_media_type',
      details: []
    });
  }

  const body = parseJson(event.body);
  if (!body.ok) {
    return json(400, {
      ok: false,
      error: 'Request body must be valid JSON.',
      code: 'invalid_json',
      details: []
    });
  }

  const result = await getController().handle({
    method: event.httpMethod,
    id: event.queryStringParameters?.id,
    query: event.queryStringParameters || {},
    body: body.value
  });

  return json(result.statusCode, result.body);
}

function parseJson(body) {
  if (!body) return { ok: true, value: {} };

  try {
    return { ok: true, value: JSON.parse(body) };
  } catch {
    return { ok: false };
  }
}

function json(statusCode, payload) {
  return {
    statusCode,
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  };
}

function isJson(headers = {}) {
  const contentType = headers['content-type'] || headers['Content-Type'] || '';
  return contentType.toLowerCase().includes('application/json');
}

function getController() {
  if (!controller) {
    controller = createReservationController();
  }

  return controller;
}
