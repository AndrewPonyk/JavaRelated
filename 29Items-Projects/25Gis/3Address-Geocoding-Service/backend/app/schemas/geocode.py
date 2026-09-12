from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, field_validator

AddressText = Annotated[str, Field(min_length=3, max_length=500)]
SourceText = Annotated[
    str,
    Field(min_length=2, max_length=50, pattern=r"^[a-zA-Z0-9_.-]+$"),
]


class StrictBaseModel(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    @field_validator("*", mode="before")
    @classmethod
    def reject_control_characters(cls, value: object) -> object:
        if isinstance(value, str) and any(ord(character) < 32 for character in value):
            raise ValueError("Control characters are not allowed")
        return value


class AddressLookupRequest(StrictBaseModel):
    query: AddressText = Field(examples=["1600 Pennsylvania Ave NW"])


class ReverseGeocodeRequest(StrictBaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class AddressCreateRequest(StrictBaseModel):
    formatted_address: AddressText
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    confidence: float = Field(default=1.0, ge=0, le=1)
    source: SourceText = "manual"


class AddressRecord(StrictBaseModel):
    id: int | None = None
    formatted_address: str
    latitude: float
    longitude: float
    confidence: float = Field(ge=0, le=1)
    source: str


class AddressListResponse(StrictBaseModel):
    items: list[AddressRecord]
    total: int
    limit: int
    offset: int


class AddressLookupResponse(StrictBaseModel):
    query: str
    normalized_query: str
    best_match: AddressRecord
    candidates: list[AddressRecord] = Field(default_factory=list)
