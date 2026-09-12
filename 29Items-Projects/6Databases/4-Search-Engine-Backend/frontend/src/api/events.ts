import { apiPost } from "./client";

export interface ClickEvent {
  query: string;
  product_id: string;
  position: number;
}

/** Fire-and-forget click feedback (LTR training signal) — never blocks the UI. */
export function postClickEvent(event: ClickEvent): void {
  void apiPost<void>("/events/click", event).catch(() => {
    /* analytics loss is acceptable; the click UX must never suffer */
  });
}
