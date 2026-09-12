import { Controller } from "@hotwired/stimulus"
import Sortable from "sortablejs"

// Handles drag-and-drop on the issue board. Each column is a sortable zone;
// dropping a card triggers a PATCH to update status and (optionally) sprint.
export default class extends Controller {
  static targets = ["column"]
  static values = { baseUrl: String }

  connect() {
    this.sortables = this.columnTargets.map((col) =>
      Sortable.create(col, {
        group: "issues",
        animation: 150,
        ghostClass: "opacity-50",
        onEnd: (event) => this.#handleDrop(event),
      })
    )
  }

  disconnect() {
    this.sortables?.forEach((s) => s.destroy())
  }

  async #handleDrop(event) {
    const issueId = event.item.dataset.issueId
    const newStatus = event.to.dataset.status
    if (!issueId || !newStatus) return

    try {
      const url = `${this.baseUrlValue}/${issueId}`
      const res = await fetch(url, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          "Accept": "text/vnd.turbo-stream.html",
          "X-CSRF-Token": document.querySelector("meta[name=csrf-token]").content,
        },
        body: JSON.stringify({ issue: { status: newStatus } }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const stream = await res.text()
      Turbo.renderStreamMessage(stream)
    } catch (err) {
      console.error("Failed to update issue status", err)
      event.from.insertBefore(event.item, event.from.children[event.oldIndex])
    }
  }
}
