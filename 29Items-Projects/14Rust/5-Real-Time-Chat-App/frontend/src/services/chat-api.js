export class ChatApiError extends Error {
  constructor(message, status = 0, code = "request_failed") {
    super(message);
    this.name = "ChatApiError";
    this.status = status;
    this.code = code;
  }
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers ?? {});
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  const response = await fetch(path, {
    credentials: "same-origin",
    ...options,
    headers,
  });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    let code = "request_failed";
    try {
      const payload = await response.json();
      message = payload.error?.message ?? message;
      code = payload.error?.code ?? code;
    } catch {
      // Proxies can return a non-JSON error page.
    }
    throw new ChatApiError(message, response.status, code);
  }
  return response.status === 204 ? null : response.json();
}

const json = (value) => JSON.stringify(value);

export const auth = {
  me: () => request("/api/auth/me"),
  register: (username, password) =>
    request("/api/auth/register", {
      method: "POST",
      body: json({ username, password }),
    }),
  login: (username, password) =>
    request("/api/auth/login", {
      method: "POST",
      body: json({ username, password }),
    }),
  logout: () => request("/api/auth/logout", { method: "POST" }),
};

export const users = {
  updateProfile: (username) =>
    request("/api/users/me", {
      method: "PUT",
      body: json({ username }),
    }),
  changePassword: (currentPassword, newPassword) =>
    request("/api/users/me/password", {
      method: "POST",
      body: json({
        current_password: currentPassword,
        new_password: newPassword,
      }),
    }),
  deleteAccount: (password) =>
    request("/api/users/me", {
      method: "DELETE",
      body: json({ password }),
    }),
};

export const rooms = {
  list: ({ signal, offset = 0, limit = 100 } = {}) => {
    const query = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    return request(`/api/rooms?${query}`, { signal });
  },
  create: (room) => request("/api/rooms", { method: "POST", body: json(room) }),
  update: (roomId, room) =>
    request(`/api/rooms/${encodeURIComponent(roomId)}`, {
      method: "PUT",
      body: json(room),
    }),
  delete: (roomId) =>
    request(`/api/rooms/${encodeURIComponent(roomId)}`, { method: "DELETE" }),
  join: (roomId) =>
    request(`/api/rooms/${encodeURIComponent(roomId)}/join`, { method: "POST" }),
  history: (roomId, before, limit = 50) => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (before) query.set("before", before);
    return request(`/api/rooms/${encodeURIComponent(roomId)}/messages?${query}`);
  },
  members: (roomId, offset = 0, limit = 100) => {
    const query = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    return request(`/api/rooms/${encodeURIComponent(roomId)}/members?${query}`);
  },
  addMember: (roomId, username, role) =>
    request(`/api/rooms/${encodeURIComponent(roomId)}/members`, {
      method: "POST",
      body: json({ username, role }),
    }),
  updateMember: (roomId, userId, role) =>
    request(
      `/api/rooms/${encodeURIComponent(roomId)}/members/${encodeURIComponent(userId)}`,
      { method: "PUT", body: json({ role }) },
    ),
  removeMember: (roomId, userId) =>
    request(
      `/api/rooms/${encodeURIComponent(roomId)}/members/${encodeURIComponent(userId)}`,
      { method: "DELETE" },
    ),
  updateMessage: (roomId, messageId, content) =>
    request(
      `/api/rooms/${encodeURIComponent(roomId)}/messages/${encodeURIComponent(messageId)}`,
      { method: "PUT", body: json({ content }) },
    ),
  deleteMessage: (roomId, messageId) =>
    request(
      `/api/rooms/${encodeURIComponent(roomId)}/messages/${encodeURIComponent(messageId)}`,
      { method: "DELETE" },
    ),
};

export function openChatSocket(roomId) {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return new WebSocket(
    `${protocol}//${window.location.host}/ws/${encodeURIComponent(roomId)}`,
  );
}
