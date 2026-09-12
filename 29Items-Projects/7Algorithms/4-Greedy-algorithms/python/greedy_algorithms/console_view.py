from collections.abc import Callable


class ConsoleView:
    """Small presentation component for consistent CLI output."""

    def section(self, title: str) -> None:
        print(f"\n=== {title} ===")

    def loading(self, label: str) -> None:
        print(f"Running {label}...")

    def result(self, label: str, value: object, proof_note: str) -> None:
        print(f"{label}: {value}")
        print(f"Why greedy works: {proof_note}")

    def error(self, label: str, exc: Exception) -> None:
        print(f"{label} failed: {exc}")

    def run_demo(self, label: str, action: Callable[[], tuple[object, str]]) -> None:
        self.section(label)
        self.loading(label)
        try:
            value, proof_note = action()
            self.result("Result", value, proof_note)
        except ValueError as exc:
            self.error(label, exc)
