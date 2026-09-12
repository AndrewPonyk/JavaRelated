from dataclasses import dataclass


@dataclass(frozen=True)
class Interval:
    name: str
    start: int
    finish: int


def schedule_intervals(intervals: list[Interval]) -> list[Interval]:
    for interval in intervals:
        if interval.finish < interval.start:
            raise ValueError(f"{interval.name} finishes before it starts")

    result: list[Interval] = []
    last_finish = -1
    for interval in sorted(intervals, key=lambda item: (item.finish, item.start, item.name)):
        if interval.start >= last_finish:
            result.append(interval)
            last_finish = interval.finish
    return result
