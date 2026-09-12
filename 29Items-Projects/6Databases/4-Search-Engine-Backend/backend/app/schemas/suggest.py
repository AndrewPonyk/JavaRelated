"""Autocomplete API contract."""

from pydantic import BaseModel


class SuggestResponse(BaseModel):
    query: str
    suggestions: list[str]
