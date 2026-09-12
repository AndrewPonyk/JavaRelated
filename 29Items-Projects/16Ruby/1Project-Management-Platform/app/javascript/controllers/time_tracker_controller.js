import { Controller } from "@hotwired/stimulus"

// Start/stop timer for ad-hoc time tracking on the issue page.
// Submits accumulated hours to the time_entries endpoint on stop.
export default class extends Controller {
  static targets = ["display", "startBtn", "stopBtn", "hoursField"]
  static values = { submitUrl: String }

  connect() {
    this.startedAt = null
    this.elapsedMs = 0
    this.render()
  }

  start() {
    this.startedAt = Date.now()
    this.ticker = setInterval(() => this.render(), 1000)
    this.startBtnTarget?.classList.add("hidden")
    this.stopBtnTarget?.classList.remove("hidden")
  }

  stop() {
    if (!this.startedAt) return
    this.elapsedMs += Date.now() - this.startedAt
    this.startedAt = null
    clearInterval(this.ticker)
    this.render()
    if (this.hasHoursFieldTarget) {
      this.hoursFieldTarget.value = (this.elapsedMs / 3_600_000).toFixed(2)
    }
    this.startBtnTarget?.classList.remove("hidden")
    this.stopBtnTarget?.classList.add("hidden")
  }

  render() {
    const current = this.startedAt ? this.elapsedMs + (Date.now() - this.startedAt) : this.elapsedMs
    const h = Math.floor(current / 3_600_000)
    const m = Math.floor((current % 3_600_000) / 60_000)
    const s = Math.floor((current % 60_000) / 1000)
    if (this.hasDisplayTarget) {
      this.displayTarget.textContent = `${String(h).padStart(2,"0")}:${String(m).padStart(2,"0")}:${String(s).padStart(2,"0")}`
    }
  }
}
