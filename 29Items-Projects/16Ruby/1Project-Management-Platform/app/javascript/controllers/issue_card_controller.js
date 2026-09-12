import { Controller } from "@hotwired/stimulus"

// Subtle hover-state + click-to-open on issue cards.
export default class extends Controller {
  connect() {
    this.element.classList.add("transition", "hover:shadow-md")
  }
}
