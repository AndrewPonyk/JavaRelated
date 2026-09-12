"""Service-layer unit tests: business logic without HTTP."""

import pytest

from app.schemas.symbolic import RenderRequest, SolveRequest
from app.services import symbolic_service
from sciengine.exceptions import ExpressionParseError


def test_solve_returns_serializable_response(app_env):
    response = symbolic_service.solve(SolveRequest(expression="x^2 - 4 = 0", variable="x"))
    assert sorted(response.solutions) == ["-2", "2"]
    assert response.cached is False
    assert response.equation_latex
    assert response.steps_latex
    assert response.derivation_latex.startswith("\\begin{aligned}")


def test_solve_cache_hit_on_second_call(app_env):
    first = symbolic_service.solve(SolveRequest(expression="x^2 - 25 = 0"))
    second = symbolic_service.solve(SolveRequest(expression="x**2 - 25 = 0"))
    assert first.cached is False
    assert second.cached is True
    assert second.solutions == first.solutions


def test_solve_propagates_parse_errors(app_env):
    # Global handler in app.main maps this to 422 problem+json.
    with pytest.raises(ExpressionParseError):
        symbolic_service.solve(SolveRequest(expression="x +* 2 = 0"))


def test_render_produces_latex(app_env):
    response = symbolic_service.render(RenderRequest(expression="sqrt(x)/2"))
    assert "\\sqrt{x}" in response.latex
