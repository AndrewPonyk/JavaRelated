"""Drug / ingredient domain schemas."""

from pydantic import BaseModel, Field, model_validator


class Ingredient(BaseModel):
    rxcui: str
    name: str


class Drug(BaseModel):
    rxcui: str = Field(..., description="RxNorm Concept Unique Identifier")
    name: str
    tty: str | None = Field(default=None, description="RxNorm term type (SCD, SBD, IN, ...)")
    synonym: str | None = None
    ingredients: list[Ingredient] = Field(default_factory=list)
    drug_classes: list[str] = Field(default_factory=list)


class DrugSearchResult(BaseModel):
    query: str
    matches: list[Drug]


class DrugInput(BaseModel):
    """Flexible drug reference: supply at least one of rxcui / ndc / name."""

    rxcui: str | None = None
    ndc: str | None = None
    name: str | None = None

    @model_validator(mode="after")
    def _at_least_one(self) -> "DrugInput":
        if not any([self.rxcui, self.ndc, self.name]):
            raise ValueError("Provide at least one of: rxcui, ndc, name")
        return self

    def label(self) -> str:
        return self.name or self.rxcui or self.ndc or "unknown"


# --------------------------------------------------------------------------- #
# Admin / ingestion schemas
# --------------------------------------------------------------------------- #
class IngredientUpsert(BaseModel):
    rxcui: str = Field(..., min_length=1)
    name: str = Field(..., min_length=1)
    class_ids: list[str] = Field(default_factory=list)


class DrugClassUpsert(BaseModel):
    class_id: str = Field(..., min_length=1)
    name: str = Field(..., min_length=1)
    class_type: str | None = None


class DrugUpsert(BaseModel):
    rxcui: str = Field(..., min_length=1)
    name: str = Field(..., min_length=1)
    tty: str | None = None
    ingredients: list[IngredientUpsert] = Field(default_factory=list)
    class_ids: list[str] = Field(default_factory=list, description="Drug-level classes")


class DrugListResponse(BaseModel):
    items: list[Drug]
    total: int
    limit: int
    offset: int
