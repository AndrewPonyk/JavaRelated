from dataclasses import dataclass


@dataclass(frozen=True)
class Item:
    name: str
    value: float
    weight: float

    @property
    def density(self) -> float:
        if self.weight <= 0:
            raise ValueError(f"{self.name} must have positive weight")
        return self.value / self.weight


@dataclass(frozen=True)
class KnapsackTake:
    name: str
    fraction: float
    value: float


def fractional_knapsack(items: list[Item], capacity: float) -> dict[str, object]:
    if capacity < 0:
        raise ValueError("capacity must be non-negative")

    remaining = capacity
    total_value = 0.0
    taken: list[KnapsackTake] = []

    for item in sorted(items, key=lambda candidate: candidate.density, reverse=True):
        if remaining <= 0:
            break
        amount = min(item.weight, remaining)
        fraction = amount / item.weight
        value = item.value * fraction
        taken.append(KnapsackTake(item.name, round(fraction, 2), round(value, 2)))
        total_value += value
        remaining -= amount

    return {"total_value": round(total_value, 2), "taken": taken}
