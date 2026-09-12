from dataclasses import asdict

try:
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel
except ImportError:  # pragma: no cover - optional API dependency
    FastAPI = None
    HTTPException = Exception
    BaseModel = object

from greedy_algorithms.algorithms.activity_selection import Activity, select_activities


if FastAPI is not None:
    app = FastAPI(title="GREEDY Algorithms API")
else:
    app = None


class ActivityInput(BaseModel):  # type: ignore[misc]
    name: str
    start: int
    finish: int


class ScenarioInput(BaseModel):  # type: ignore[misc]
    name: str
    activities: list[ActivityInput]


SCENARIOS: dict[str, ScenarioInput] = {}


if app is not None:

    @app.post("/scenarios")
    def create_scenario(payload: ScenarioInput) -> dict[str, str]:
        if payload.name in SCENARIOS:
            raise HTTPException(status_code=409, detail="Scenario already exists")
        SCENARIOS[payload.name] = payload
        return {"name": payload.name, "status": "created"}

    @app.get("/scenarios/{name}/activity-selection")
    def run_activity_selection(name: str) -> dict[str, object]:
        scenario = SCENARIOS.get(name)
        if scenario is None:
            raise HTTPException(status_code=404, detail="Scenario not found")

        activities = [
            Activity(item.name, item.start, item.finish)
            for item in scenario.activities
        ]
        selected = select_activities(activities)
        return {"scenario": name, "selected": [asdict(activity) for activity in selected]}
