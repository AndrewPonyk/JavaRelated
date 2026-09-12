from dataclasses import dataclass


@dataclass(frozen=True)
class Job:
    name: str
    deadline: int
    profit: int


def schedule_jobs(jobs: list[Job]) -> dict[str, object]:
    if any(job.deadline <= 0 for job in jobs):
        raise ValueError("job deadlines must be positive")

    max_deadline = max((job.deadline for job in jobs), default=0)
    slots: list[Job | None] = [None] * max_deadline

    for job in sorted(jobs, key=lambda item: item.profit, reverse=True):
        for slot in range(min(job.deadline, max_deadline) - 1, -1, -1):
            if slots[slot] is None:
                slots[slot] = job
                break

    selected = [job for job in slots if job is not None]
    return {
        "jobs": selected,
        "total_profit": sum(job.profit for job in selected),
    }
