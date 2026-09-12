from dataclasses import dataclass


@dataclass(frozen=True)
class Activity:
    name: str
    start: int
    finish: int


def select_activities(activities: list[Activity]) -> list[Activity]:
    for activity in activities:
        if activity.finish < activity.start:
            raise ValueError(f"{activity.name} finishes before it starts")

    selected: list[Activity] = []
    current_finish = -1
    for activity in sorted(activities, key=lambda item: (item.finish, item.start, item.name)):
        if activity.start >= current_finish:
            selected.append(activity)
            current_finish = activity.finish
    return selected
