export class TaskConsole {
  constructor(root, options = {}) {
    this.root = root;
    this.apiKey = localStorage.getItem("taskQueueApiKey") || options.apiKey || "";
    this.state = {
      loading: false,
      error: null,
      tasks: [],
      deadLetters: [],
      stats: null,
      status: "",
    };
  }

  async request(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": this.apiKey,
        ...(options.headers || {}),
      },
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || `Request failed with status ${response.status}`);
    }
    if (response.status === 204 || response.status === 202) {
      return null;
    }
    return response.json();
  }

  async load() {
    this.setState({ loading: true, error: null });
    try {
      const query = this.state.status ? `?status=${encodeURIComponent(this.state.status)}` : "";
      const [tasks, deadLetters, stats] = await Promise.all([
        this.request(`/api/v1/tasks${query}`),
        this.request("/api/v1/dead-letters"),
        this.request("/api/v1/queue/stats"),
      ]);
      this.setState({ loading: false, tasks, deadLetters, stats });
    } catch (error) {
      this.setState({ loading: false, error: error.message });
    }
  }

  async createTask(form) {
    const data = new FormData(form);
    const payloadText = String(data.get("payload") || "{}");
    let payload;
    try {
      payload = JSON.parse(payloadText);
    } catch {
      throw new Error("Payload must be valid JSON.");
    }
    if (!data.get("type")) {
      throw new Error("Task type is required.");
    }
    await this.request("/api/v1/tasks", {
      method: "POST",
      body: JSON.stringify({
        type: data.get("type"),
        payload,
        max_attempts: Number(data.get("max_attempts") || 3),
        delay_seconds: Number(data.get("delay_seconds") || 0),
        timeout_seconds: Number(data.get("timeout_seconds") || 30),
        idempotency_key: String(data.get("idempotency_key") || ""),
      }),
    });
    await this.load();
  }

  async cancelTask(id) {
    await this.request(`/api/v1/tasks/${id}/cancel`, { method: "POST" });
    await this.load();
  }

  async deleteTask(id) {
    await this.request(`/api/v1/tasks/${id}`, { method: "DELETE" });
    await this.load();
  }

  async requeueTask(id) {
    await this.request(`/api/v1/dead-letters/${id}/requeue`, { method: "POST" });
    await this.load();
  }

  setStatus(status) {
    this.state.status = status;
    return this.load();
  }

  setAPIKey(apiKey) {
    this.apiKey = apiKey;
    localStorage.setItem("taskQueueApiKey", apiKey);
  }

  setState(nextState) {
    this.state = { ...this.state, ...nextState };
    this.render();
  }

  render() {
    const stats = this.state.stats || { pending: 0, reserved: 0, retry: 0, dead_letters: 0 };
    this.root.innerHTML = `
      <section class="stats">
        ${statCard("Pending", stats.pending)}
        ${statCard("Reserved", stats.reserved)}
        ${statCard("Retry", stats.retry)}
        ${statCard("Dead letters", stats.dead_letters)}
      </section>
      ${this.state.loading ? `<p class="muted">Loading tasks...</p>` : ""}
      ${this.state.error ? `<p class="error">${escapeHTML(this.state.error)}</p>` : ""}
      <section>
        <div class="section-title">
          <h2>Tasks</h2>
          <select id="status-filter">
            ${option("", "All", this.state.status)}
            ${option("pending", "Pending", this.state.status)}
            ${option("running", "Running", this.state.status)}
            ${option("retrying", "Retrying", this.state.status)}
            ${option("completed", "Completed", this.state.status)}
            ${option("cancelled", "Cancelled", this.state.status)}
            ${option("dead_lettered", "Dead-lettered", this.state.status)}
          </select>
        </div>
        ${renderTaskTable(this.state.tasks)}
      </section>
      <section>
        <div class="section-title">
          <h2>Dead Letters</h2>
        </div>
        ${renderTaskTable(this.state.deadLetters, true)}
      </section>
    `;
    this.root.querySelector("#status-filter")?.addEventListener("change", (event) => {
      this.setStatus(event.target.value);
    });
    this.root.querySelectorAll("[data-cancel]").forEach((button) => {
      button.addEventListener("click", () => this.cancelTask(button.dataset.cancel));
    });
    this.root.querySelectorAll("[data-delete]").forEach((button) => {
      button.addEventListener("click", () => this.deleteTask(button.dataset.delete));
    });
    this.root.querySelectorAll("[data-requeue]").forEach((button) => {
      button.addEventListener("click", () => this.requeueTask(button.dataset.requeue));
    });
  }
}

function statCard(label, value) {
  return `<article><span>${escapeHTML(label)}</span><strong>${Number(value || 0)}</strong></article>`;
}

function option(value, label, selected) {
  return `<option value="${escapeHTML(value)}" ${value === selected ? "selected" : ""}>${escapeHTML(label)}</option>`;
}

function renderTaskTable(tasks, deadLetterMode = false) {
  if (!tasks || tasks.length === 0) {
    return `<p class="muted">No records.</p>`;
  }
  return `
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>Type</th>
          <th>Status</th>
          <th>Attempts</th>
          <th>Updated</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        ${tasks.map((task) => renderTaskRow(task, deadLetterMode)).join("")}
      </tbody>
    </table>
  `;
}

function renderTaskRow(task, deadLetterMode) {
  const id = escapeHTML(task.id || "");
  const terminal = ["completed", "failed", "cancelled", "dead_lettered"].includes(task.status);
  return `
    <tr>
      <td><code>${id}</code></td>
      <td>${escapeHTML(task.type || "")}</td>
      <td><span class="status">${escapeHTML(task.status || "")}</span></td>
      <td>${Number(task.attempts || 0)} / ${Number(task.max_attempts || 0)}</td>
      <td>${escapeHTML(task.updated_at || "")}</td>
      <td class="actions">
        ${!terminal ? `<button type="button" data-cancel="${id}">Cancel</button>` : ""}
        ${deadLetterMode ? `<button type="button" data-requeue="${id}">Requeue</button>` : ""}
        <button type="button" class="secondary" data-delete="${id}">Delete</button>
      </td>
    </tr>
  `;
}

function escapeHTML(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
