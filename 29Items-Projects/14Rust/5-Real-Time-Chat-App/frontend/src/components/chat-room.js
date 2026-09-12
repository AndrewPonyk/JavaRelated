import { auth, openChatSocket, rooms, users } from "../services/chat-api.js";

export class ChatRoom {
  #root;
  #identity = null;
  #rooms = [];
  #selectedRoom = null;
  #members = [];
  #roomOffset = 0;
  #memberOffset = 0;
  #messages = new Map();
  #nextCursor = null;
  #socket = null;
  #abortController = null;
  #manualClose = false;
  #reconnectAttempt = 0;
  #reconnectTimer = null;
  #heartbeatTimer = null;
  #typingTimer = null;

  constructor(root) {
    this.#root = root;
    this.#root.innerHTML = `
      <section id="auth-view" class="auth-view" aria-labelledby="auth-title">
        <div class="auth-card">
          <div class="brand hero-brand"><span class="brand-mark" aria-hidden="true">R</span><div><h1>Rust Rooms</h1><p>Durable, real-time conversations</p></div></div>
          <h2 id="auth-title">Welcome back</h2>
          <p id="auth-copy" class="muted">Sign in to your account.</p>
          <form id="auth-form" data-mode="login">
            <label for="auth-username">Username</label>
            <input id="auth-username" minlength="3" maxlength="40" autocomplete="username" required>
            <label for="auth-password">Password</label>
            <input id="auth-password" type="password" minlength="12" maxlength="128" autocomplete="current-password" required>
            <button id="auth-submit" type="submit">Sign in</button>
          </form>
          <button id="auth-toggle" class="text-button" type="button">Create an account</button>
          <div id="auth-error" class="error-banner hidden" role="alert"></div>
        </div>
      </section>

      <section id="chat-view" class="chat-shell hidden" aria-labelledby="page-title">
        <aside class="rooms-panel">
          <div class="brand"><span class="brand-mark" aria-hidden="true">R</span><div><h1 id="page-title">Rust Rooms</h1><p>Live conversations</p></div></div>
          <div class="user-card"><div><strong id="current-user"></strong><span>Signed in</span></div><div class="user-actions"><button id="account" class="text-button" type="button">Account</button><button id="logout" class="text-button" type="button">Log out</button></div></div>
          <form id="create-room-form" class="stack-form">
            <label for="new-room">Create a room</label>
            <input id="new-room" maxlength="80" placeholder="Room name" required>
            <input id="new-description" maxlength="280" placeholder="Short description">
            <label class="check-label"><input id="new-private" type="checkbox"> Private room</label>
            <button type="submit">Create room</button>
          </form>
          <div id="rooms-state" class="panel-state" role="status"></div>
          <nav id="rooms" class="room-list" aria-label="Chat rooms"></nav>
          <button id="load-more-rooms" class="secondary hidden" type="button">Load more rooms</button>
          <button id="retry-rooms" class="secondary hidden" type="button">Retry</button>
        </aside>

        <main class="conversation-panel">
          <header class="conversation-header">
            <div><p class="eyebrow">Current room</p><h2 id="room-title">Choose a room</h2><p id="room-description" class="muted"></p></div>
            <div class="header-actions">
              <div class="presence"><span class="presence-dot"></span><span id="online-count">Offline</span></div>
              <button id="edit-room" class="secondary hidden" type="button">Edit</button>
              <button id="delete-room" class="danger hidden" type="button">Delete</button>
            </div>
          </header>
          <div id="connection-state" class="connection-state" role="status">Select a room to begin.</div>
          <button id="load-older" class="load-older hidden" type="button">Load older messages</button>
          <ol id="messages" class="messages" aria-live="polite" aria-label="Messages"></ol>
          <div id="typing-state" class="typing-state" aria-live="polite"></div>
          <div id="chat-error" class="error-banner hidden" role="alert"></div>
          <form id="message-form" class="message-form">
            <label class="sr-only" for="message">Message</label>
            <input id="message" maxlength="4096" placeholder="Write a message…" autocomplete="off" disabled required>
            <button id="send-message" type="submit" disabled>Send</button>
          </form>
        </main>

        <aside class="members-panel">
          <div class="members-heading"><div><p class="eyebrow">Room access</p><h2>Members</h2></div><span id="member-count" class="count-badge">0</span></div>
          <form id="invite-form" class="stack-form hidden">
            <label for="invite-username">Invite a user</label>
            <input id="invite-username" maxlength="40" placeholder="Username" required>
            <select id="invite-role"><option value="member">Member</option><option value="moderator">Moderator</option></select>
            <button type="submit">Add member</button>
          </form>
          <ul id="members" class="member-list"></ul>
          <button id="load-more-members" class="secondary hidden" type="button">Load more members</button>
        </aside>
      </section>

      <dialog id="account-dialog" class="account-dialog" aria-labelledby="account-title">
        <div class="dialog-heading"><h2 id="account-title">Account settings</h2><button id="close-account" class="text-button" type="button" aria-label="Close account settings">Close</button></div>
        <form id="profile-form" class="stack-form">
          <label for="profile-username">Username</label>
          <input id="profile-username" minlength="3" maxlength="40" autocomplete="username" required>
          <button type="submit">Save username</button>
        </form>
        <form id="password-form" class="stack-form">
          <label for="current-password">Current password</label>
          <input id="current-password" type="password" minlength="12" maxlength="128" autocomplete="current-password" required>
          <label for="new-password">New password</label>
          <input id="new-password" type="password" minlength="12" maxlength="128" autocomplete="new-password" required>
          <button type="submit">Change password</button>
        </form>
        <form id="delete-account-form" class="stack-form danger-zone">
          <label for="delete-password">Delete account</label>
          <p class="muted">Owned rooms and their messages will be permanently deleted.</p>
          <input id="delete-password" type="password" minlength="12" maxlength="128" autocomplete="current-password" placeholder="Current password" required>
          <button class="danger" type="submit">Delete account</button>
        </form>
        <div id="account-error" class="error-banner hidden" role="alert"></div>
      </dialog>`;

    this.#bindEvents();
  }

  async start() {
    this.#setAuthBusy(true);
    try {
      this.#identity = await auth.me();
      await this.#enterApplication();
    } catch (error) {
      if (error.status !== 401) this.#showAuthError(error.message);
    } finally {
      this.#setAuthBusy(false);
    }
  }

  destroy() {
    this.#abortController?.abort();
    this.#disconnect(true);
  }

  #bindEvents() {
    this.#el("auth-form").addEventListener("submit", (event) => this.#submitAuth(event));
    this.#el("auth-toggle").addEventListener("click", () => this.#toggleAuthMode());
    this.#el("logout").addEventListener("click", () => this.#logout());
    this.#el("account").addEventListener("click", () => this.#openAccount());
    this.#el("close-account").addEventListener("click", () => this.#el("account-dialog").close());
    this.#el("profile-form").addEventListener("submit", (event) => this.#updateProfile(event));
    this.#el("password-form").addEventListener("submit", (event) => this.#changePassword(event));
    this.#el("delete-account-form").addEventListener("submit", (event) => this.#deleteAccount(event));
    this.#el("retry-rooms").addEventListener("click", () => this.#loadRooms());
    this.#el("load-more-rooms").addEventListener("click", () => this.#loadRooms(false));
    this.#el("create-room-form").addEventListener("submit", (event) => this.#createRoom(event));
    this.#el("edit-room").addEventListener("click", () => this.#editRoom());
    this.#el("delete-room").addEventListener("click", () => this.#deleteRoom());
    this.#el("load-older").addEventListener("click", () => this.#loadHistory(true));
    this.#el("message-form").addEventListener("submit", (event) => this.#send(event));
    this.#el("message").addEventListener("input", () => this.#sendTyping());
    this.#el("invite-form").addEventListener("submit", (event) => this.#invite(event));
    this.#el("load-more-members").addEventListener("click", () => this.#loadMembers(false));
  }

  async #submitAuth(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const username = this.#el("auth-username").value.trim();
    const password = this.#el("auth-password").value;
    this.#setAuthBusy(true);
    this.#clearAuthError();
    try {
      this.#identity = form.dataset.mode === "register"
        ? await auth.register(username, password)
        : await auth.login(username, password);
      form.reset();
      await this.#enterApplication();
    } catch (error) {
      this.#showAuthError(error.message);
    } finally {
      this.#setAuthBusy(false);
    }
  }

  #toggleAuthMode() {
    const form = this.#el("auth-form");
    const registering = form.dataset.mode !== "register";
    form.dataset.mode = registering ? "register" : "login";
    this.#el("auth-title").textContent = registering ? "Create your account" : "Welcome back";
    this.#el("auth-copy").textContent = registering
      ? "Use 3–40 characters and a 12+ character password with a letter and number."
      : "Sign in to your account.";
    this.#el("auth-submit").textContent = registering ? "Create account" : "Sign in";
    this.#el("auth-toggle").textContent = registering ? "I already have an account" : "Create an account";
    this.#el("auth-password").autocomplete = registering ? "new-password" : "current-password";
    this.#clearAuthError();
  }

  async #enterApplication() {
    this.#el("auth-view").classList.add("hidden");
    this.#el("chat-view").classList.remove("hidden");
    this.#el("current-user").textContent = this.#identity.user.username;
    await this.#loadRooms();
  }

  async #logout() {
    try {
      await auth.logout();
    } finally {
      this.#disconnect(true);
      this.#identity = null;
      this.#selectedRoom = null;
      this.#el("chat-view").classList.add("hidden");
      this.#el("auth-view").classList.remove("hidden");
    }
  }

  #openAccount() {
    this.#el("profile-username").value = this.#identity.user.username;
    this.#clearAccountError();
    this.#el("account-dialog").showModal();
  }

  async #updateProfile(event) {
    event.preventDefault();
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    this.#clearAccountError();
    try {
      const user = await users.updateProfile(this.#el("profile-username").value.trim());
      this.#identity.user = user;
      this.#el("current-user").textContent = user.username;
      if (this.#selectedRoom) await this.#selectRoom(this.#selectedRoom);
    } catch (error) {
      this.#showAccountError(error.message);
    } finally {
      button.disabled = false;
    }
  }

  async #changePassword(event) {
    event.preventDefault();
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    this.#clearAccountError();
    try {
      await users.changePassword(
        this.#el("current-password").value,
        this.#el("new-password").value,
      );
      event.currentTarget.reset();
      this.#el("account-dialog").close();
      this.#disconnect(true);
      this.#identity = null;
      this.#selectedRoom = null;
      this.#el("chat-view").classList.add("hidden");
      this.#el("auth-view").classList.remove("hidden");
      this.#showAuthError("Password changed. Sign in again on every device.");
    } catch (error) {
      this.#showAccountError(error.message);
    } finally {
      button.disabled = false;
    }
  }

  async #deleteAccount(event) {
    event.preventDefault();
    if (!window.confirm("Delete your account and every room you own? This cannot be undone.")) return;
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    this.#clearAccountError();
    try {
      await users.deleteAccount(this.#el("delete-password").value);
      this.#el("account-dialog").close();
      this.#disconnect(true);
      this.#identity = null;
      this.#selectedRoom = null;
      this.#el("chat-view").classList.add("hidden");
      this.#el("auth-view").classList.remove("hidden");
      this.#showAuthError("Your account was deleted.");
    } catch (error) {
      this.#showAccountError(error.message);
    } finally {
      button.disabled = false;
    }
  }

  async #loadRooms(reset = true) {
    this.#abortController?.abort();
    this.#abortController = new AbortController();
    if (reset) this.#roomOffset = 0;
    this.#setRoomsState("Loading rooms…", true);
    this.#el("retry-rooms").classList.add("hidden");
    try {
      const page = await rooms.list({
        signal: this.#abortController.signal,
        offset: this.#roomOffset,
      });
      this.#rooms = reset ? page : [...this.#rooms, ...page];
      this.#roomOffset = this.#rooms.length;
      this.#el("load-more-rooms").classList.toggle("hidden", page.length === 0);
      this.#renderRooms();
      this.#setRoomsState(this.#rooms.length ? "" : "No rooms yet. Create one.");
      if (this.#selectedRoom) {
        const refreshed = this.#rooms.find((room) => room.id === this.#selectedRoom.id);
        if (refreshed) this.#selectedRoom = refreshed;
      }
    } catch (error) {
      if (error.name === "AbortError") return;
      this.#setRoomsState(`Could not load rooms: ${error.message}`);
      this.#el("retry-rooms").classList.remove("hidden");
    }
  }

  #renderRooms() {
    const container = this.#el("rooms");
    container.replaceChildren();
    for (const room of this.#rooms) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "room-button";
      button.classList.toggle("selected", room.id === this.#selectedRoom?.id);
      button.innerHTML = `<span></span><small></small>`;
      button.querySelector("span").textContent = `${room.is_private ? "🔒" : "#"} ${room.name}`;
      button.querySelector("small").textContent = `${room.member_count} members · ${room.online_count} online`;
      button.addEventListener("click", () => this.#selectRoom(room));
      container.append(button);
    }
  }

  async #createRoom(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button");
    button.disabled = true;
    this.#clearError();
    try {
      const room = await rooms.create({
        name: this.#el("new-room").value.trim(),
        description: this.#el("new-description").value.trim(),
        is_private: this.#el("new-private").checked,
      });
      form.reset();
      await this.#loadRooms();
      await this.#selectRoom(this.#rooms.find((candidate) => candidate.id === room.id));
    } catch (error) {
      this.#showError(error.message);
    } finally {
      button.disabled = false;
    }
  }

  async #selectRoom(room) {
    if (!room) return;
    this.#disconnect(true);
    this.#selectedRoom = room;
    this.#messages.clear();
    this.#nextCursor = null;
    this.#renderRooms();
    this.#el("room-title").textContent = room.name;
    this.#el("room-description").textContent = room.description || "No description";
    this.#el("online-count").textContent = `${room.online_count} online`;
    this.#el("messages").replaceChildren();
    this.#setConnection("Joining room…");
    this.#setConnected(false);
    try {
      if (!room.current_user_role) {
        const membership = await rooms.join(room.id);
        room.current_user_role = membership.role;
        room.member_count += 1;
      }
      this.#updateRoomControls();
      await Promise.all([this.#loadHistory(false), this.#loadMembers()]);
      this.#manualClose = false;
      this.#connect();
    } catch (error) {
      this.#setConnection("Unable to join");
      this.#showError(error.message);
    }
  }

  #connect() {
    if (!this.#selectedRoom || this.#manualClose) return;
    const socket = openChatSocket(this.#selectedRoom.id);
    this.#socket = socket;
    this.#setConnection(this.#reconnectAttempt ? "Reconnecting…" : "Connecting…");
    socket.addEventListener("open", async () => {
      if (socket !== this.#socket) return;
      this.#reconnectAttempt = 0;
      this.#setConnected(true);
      this.#setConnection(`Connected as ${this.#identity.user.username}`);
      await this.#loadHistory(false, true);
      this.#heartbeatTimer = window.setInterval(() => {
        this.#sendCommand({ type: "ping", nonce: crypto.randomUUID() });
      }, 20_000);
    });
    socket.addEventListener("message", (event) => this.#handleEvent(event.data));
    socket.addEventListener("error", () => this.#showError("The live connection failed."));
    socket.addEventListener("close", (event) => {
      if (socket !== this.#socket) return;
      this.#setConnected(false);
      clearInterval(this.#heartbeatTimer);
      if (this.#manualClose || event.code === 1000) {
        this.#setConnection("Disconnected");
        return;
      }
      this.#scheduleReconnect();
    });
  }

  #scheduleReconnect() {
    if (this.#reconnectAttempt >= 6) {
      this.#setConnection("Disconnected — select the room to retry");
      this.#showError("Could not restore the live connection.");
      return;
    }
    const delay = Math.min(1000 * 2 ** this.#reconnectAttempt, 15_000) + Math.random() * 500;
    this.#reconnectAttempt += 1;
    this.#setConnection(`Reconnecting in ${Math.ceil(delay / 1000)}s…`);
    this.#reconnectTimer = window.setTimeout(() => this.#connect(), delay);
  }

  #disconnect(manual) {
    this.#manualClose = manual;
    clearTimeout(this.#reconnectTimer);
    clearInterval(this.#heartbeatTimer);
    clearTimeout(this.#typingTimer);
    if (this.#socket) this.#socket.close(1000, "view changed");
    this.#socket = null;
    this.#setConnected(false);
  }

  async #loadHistory(older = false, silent = false) {
    if (!this.#selectedRoom) return;
    const button = this.#el("load-older");
    button.disabled = true;
    if (!silent) this.#setConnection(older ? "Loading older messages…" : "Loading history…");
    try {
      const page = await rooms.history(this.#selectedRoom.id, older ? this.#nextCursor : null);
      if (!older) this.#messages.clear();
      for (const message of page.messages) this.#messages.set(message.id, message);
      this.#nextCursor = page.next_cursor;
      button.classList.toggle("hidden", !this.#nextCursor);
      this.#renderMessages();
    } catch (error) {
      this.#showError(`Could not load history: ${error.message}`);
    } finally {
      button.disabled = false;
    }
  }

  #send(event) {
    event.preventDefault();
    const input = this.#el("message");
    const content = input.value.trim();
    if (!content || !this.#sendCommand({
      type: "send_message",
      client_message_id: crypto.randomUUID(),
      content,
    })) return;
    input.value = "";
    this.#sendCommand({ type: "typing", is_typing: false });
    input.focus();
  }

  #sendTyping() {
    if (!this.#selectedRoom || this.#socket?.readyState !== WebSocket.OPEN) return;
    this.#sendCommand({ type: "typing", is_typing: true });
    clearTimeout(this.#typingTimer);
    this.#typingTimer = window.setTimeout(
      () => this.#sendCommand({ type: "typing", is_typing: false }),
      1200,
    );
  }

  #sendCommand(command) {
    if (this.#socket?.readyState !== WebSocket.OPEN) return false;
    this.#socket.send(JSON.stringify(command));
    return true;
  }

  #handleEvent(raw) {
    let event;
    try {
      event = JSON.parse(raw);
    } catch {
      this.#showError("The server returned an invalid event.");
      return;
    }
    if (event.type === "message_created" || event.type === "message_updated") {
      this.#messages.set(event.message.id, event.message);
      this.#renderMessages();
    } else if (event.type === "message_deleted") {
      const message = this.#messages.get(event.message_id);
      if (message) {
        message.content = "";
        message.deleted_at = event.deleted_at;
        this.#renderMessages();
      }
    } else if (["user_joined", "user_left", "online_count"].includes(event.type)) {
      this.#el("online-count").textContent = `${event.online_count} online`;
    } else if (event.type === "typing" && event.user_id !== this.#identity.user.id) {
      this.#el("typing-state").textContent = event.is_typing ? `${event.username} is typing…` : "";
    } else if (event.type === "error") {
      this.#showError(event.message);
      if (event.code === "resync_required") this.#loadHistory(false, true);
    }
  }

  #renderMessages() {
    const list = this.#el("messages");
    const atBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
    list.replaceChildren();
    const role = this.#selectedRoom?.current_user_role;
    const canModerate = role === "owner" || role === "moderator";
    const ordered = [...this.#messages.values()].sort(
      (a, b) => new Date(a.sent_at) - new Date(b.sent_at) || a.id.localeCompare(b.id),
    );
    for (const message of ordered) {
      const item = document.createElement("li");
      item.dataset.messageId = message.id;
      if (message.sender_user_id === this.#identity.user.id) item.classList.add("own-message");
      const heading = document.createElement("div");
      const sender = document.createElement("strong");
      const time = document.createElement("time");
      const body = document.createElement("p");
      sender.textContent = message.sender;
      time.dateTime = message.sent_at;
      time.textContent = new Date(message.sent_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
      body.textContent = message.deleted_at ? "Message deleted" : message.content;
      if (message.deleted_at) body.className = "deleted-message";
      heading.append(sender, time);
      if (message.edited_at && !message.deleted_at) {
        const edited = document.createElement("span");
        edited.className = "edited-label";
        edited.textContent = "edited";
        heading.append(edited);
      }
      if (!message.deleted_at && (message.sender_user_id === this.#identity.user.id || canModerate)) {
        const actions = document.createElement("span");
        actions.className = "message-actions";
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "Edit";
        edit.addEventListener("click", () => this.#editMessage(message));
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "Delete";
        remove.addEventListener("click", () => this.#deleteMessage(message));
        actions.append(edit, remove);
        heading.append(actions);
      }
      item.append(heading, body);
      list.append(item);
    }
    if (atBottom) list.scrollTop = list.scrollHeight;
  }

  async #editMessage(message) {
    const content = window.prompt("Edit message", message.content);
    if (content === null || !content.trim()) return;
    try {
      const updated = await rooms.updateMessage(
        this.#selectedRoom.id,
        message.id,
        content.trim(),
      );
      this.#messages.set(updated.id, updated);
      this.#renderMessages();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  async #deleteMessage(message) {
    if (!window.confirm("Delete this message?")) return;
    try {
      await rooms.deleteMessage(this.#selectedRoom.id, message.id);
      message.content = "";
      message.deleted_at = new Date().toISOString();
      this.#renderMessages();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  async #loadMembers(reset = true) {
    if (!this.#selectedRoom) return;
    if (reset) this.#memberOffset = 0;
    try {
      const page = await rooms.members(this.#selectedRoom.id, this.#memberOffset);
      this.#members = reset ? page : [...this.#members, ...page];
      this.#memberOffset = this.#members.length;
      this.#el("load-more-members").classList.toggle("hidden", page.length === 0);
      this.#renderMembers();
    } catch (error) {
      this.#showError(`Could not load members: ${error.message}`);
    }
  }

  #renderMembers() {
    const list = this.#el("members");
    list.replaceChildren();
    this.#el("member-count").textContent = String(this.#members.length);
    const myRole = this.#selectedRoom?.current_user_role;
    for (const member of this.#members) {
      const item = document.createElement("li");
      const identity = document.createElement("div");
      const name = document.createElement("strong");
      const role = document.createElement("span");
      name.textContent = member.username;
      role.textContent = member.role;
      identity.append(name, role);
      item.append(identity);
      if (myRole === "owner" && member.user_id !== this.#identity.user.id) {
        const actions = document.createElement("div");
        actions.className = "member-actions";
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "text-button";
        toggle.textContent = member.role === "moderator" ? "Make member" : "Make moderator";
        toggle.addEventListener("click", () => this.#changeRole(member));
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "text-button danger-text";
        remove.textContent = "Remove";
        remove.addEventListener("click", () => this.#removeMember(member));
        actions.append(toggle, remove);
        item.append(actions);
      }
      list.append(item);
    }
  }

  async #invite(event) {
    event.preventDefault();
    try {
      await rooms.addMember(
        this.#selectedRoom.id,
        this.#el("invite-username").value.trim(),
        this.#el("invite-role").value,
      );
      event.currentTarget.reset();
      await this.#loadMembers();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  async #changeRole(member) {
    try {
      await rooms.updateMember(
        this.#selectedRoom.id,
        member.user_id,
        member.role === "moderator" ? "member" : "moderator",
      );
      await this.#loadMembers();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  async #removeMember(member) {
    if (!window.confirm(`Remove ${member.username} from this room?`)) return;
    try {
      await rooms.removeMember(this.#selectedRoom.id, member.user_id);
      await this.#loadMembers();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  #updateRoomControls() {
    const role = this.#selectedRoom?.current_user_role;
    this.#el("edit-room").classList.toggle("hidden", !["owner", "moderator"].includes(role));
    this.#el("delete-room").classList.toggle("hidden", role !== "owner");
    this.#el("invite-form").classList.toggle("hidden", !["owner", "moderator"].includes(role));
    this.#el("invite-role").querySelector('option[value="moderator"]').disabled = role !== "owner";
  }

  async #editRoom() {
    const name = window.prompt("Room name", this.#selectedRoom.name);
    if (name === null) return;
    const description = window.prompt("Room description", this.#selectedRoom.description);
    if (description === null) return;
    try {
      await rooms.update(this.#selectedRoom.id, {
        name: name.trim(),
        description: description.trim(),
        is_private: this.#selectedRoom.is_private,
      });
      await this.#loadRooms();
      await this.#selectRoom(this.#rooms.find((room) => room.id === this.#selectedRoom.id));
    } catch (error) {
      this.#showError(error.message);
    }
  }

  async #deleteRoom() {
    if (!window.confirm(`Delete #${this.#selectedRoom.name} and all of its messages?`)) return;
    try {
      await rooms.delete(this.#selectedRoom.id);
      this.#disconnect(true);
      this.#selectedRoom = null;
      this.#messages.clear();
      this.#el("room-title").textContent = "Choose a room";
      this.#el("messages").replaceChildren();
      await this.#loadRooms();
    } catch (error) {
      this.#showError(error.message);
    }
  }

  #setConnected(connected) {
    this.#el("message").disabled = !connected;
    this.#el("send-message").disabled = !connected;
  }

  #setConnection(message) {
    this.#el("connection-state").textContent = message;
  }

  #setRoomsState(message, loading = false) {
    const state = this.#el("rooms-state");
    state.textContent = message;
    state.classList.toggle("loading", loading);
  }

  #setAuthBusy(busy) {
    this.#el("auth-submit").disabled = busy;
  }

  #showError(message) {
    const banner = this.#el("chat-error");
    banner.textContent = message;
    banner.classList.remove("hidden");
  }

  #clearError() {
    const banner = this.#el("chat-error");
    banner.textContent = "";
    banner.classList.add("hidden");
  }

  #showAuthError(message) {
    const banner = this.#el("auth-error");
    banner.textContent = message;
    banner.classList.remove("hidden");
  }

  #clearAuthError() {
    this.#el("auth-error").classList.add("hidden");
  }

  #showAccountError(message) {
    const banner = this.#el("account-error");
    banner.textContent = message;
    banner.classList.remove("hidden");
  }

  #clearAccountError() {
    const banner = this.#el("account-error");
    banner.textContent = "";
    banner.classList.add("hidden");
  }

  #el(id) {
    return this.#root.querySelector(`#${id}`);
  }
}
