import json

from src.api.authorizer import handler


def test_authorizer_allows_valid_country() -> None:
    token_claims = {
        "sub": "user-456",
        "custom:tenant_id": "tenant-alpha",
        "custom:role": "ADMIN",
        "custom:allowed_countries": "US,CA,GB",
    }
    event = {
        "authorizationToken": f"Bearer {json.dumps(token_claims)}",
        "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api/dev/GET/v1/usage",
        "headers": {
            "CloudFront-Viewer-Country": "US",
        },
    }

    result = handler(event, None)

    assert result["policyDocument"]["Statement"][0]["Effect"] == "Allow"
    assert result["context"]["tenant_id"] == "tenant-alpha"
    assert result["context"]["country"] == "US"


def test_authorizer_denies_restricted_country() -> None:
    token_claims = {
        "sub": "user-456",
        "custom:tenant_id": "tenant-alpha",
        "custom:role": "ADMIN",
        "custom:allowed_countries": "US,CA",
    }
    event = {
        "authorizationToken": f"Bearer {json.dumps(token_claims)}",
        "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api/dev/GET/v1/usage",
        "headers": {
            "CloudFront-Viewer-Country": "KP",  # Blocked country
        },
    }

    result = handler(event, None)

    assert result["policyDocument"]["Statement"][0]["Effect"] == "Deny"


def test_authorizer_denies_missing_token() -> None:
    event = {
        "authorizationToken": "InvalidPrefix xyz",
        "methodArn": "arn:aws:execute-api:us-east-1:123456789012:api/dev/GET/v1/usage",
    }

    result = handler(event, None)

    assert result["policyDocument"]["Statement"][0]["Effect"] == "Deny"
