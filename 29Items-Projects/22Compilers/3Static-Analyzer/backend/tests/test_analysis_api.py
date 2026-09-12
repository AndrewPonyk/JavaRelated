from app.main import create_app
from fastapi.testclient import TestClient

MISSING_ID = "11111111-1111-1111-1111-111111111111"

SAMPLE_CODE = """
#include <stdlib.h>
#include <string.h>

int main(int argc, char** argv) {
  char* command = getenv("CMD");
  system(command);
  char* p = NULL;
  *p = 'x';
  if (argc > 0) {
    strcpy(command, argv[0]);
  }
  return 0;
  system("never");
}
"""


def test_health_and_rules() -> None:
    with TestClient(create_app()) as client:
        health = client.get("/api/health")
        assert health.status_code == 200
        assert health.json()["status"] == "ok"
        assert health.headers["x-content-type-options"] == "nosniff"

        rules = client.get("/api/rules")
        assert rules.status_code == 200
        rule_ids = {rule["id"] for rule in rules.json()}
        assert "security.unsafe-call" in rule_ids
        assert "bug.null-dereference" in rule_ids

        default_rules = client.get("/api/rules?rule_pack=default")
        assert default_rules.status_code == 200
        assert len(default_rules.json()) == len(rules.json())

        missing_rules = client.get("/api/rules?rule_pack=missing")
        assert missing_rules.status_code == 400


def test_analysis_crud_findings_and_sarif() -> None:
    with TestClient(create_app()) as client:
        created = client.post(
            "/api/analyses",
            json={
                "project_name": "sample",
                "source_path": "sample.cpp",
                "source_code": SAMPLE_CODE,
            },
        )
        assert created.status_code == 201
        body = created.json()
        assert body["status"] == "completed"
        assert body["ast_facts"]["sourcePath"] == "sample.cpp"

        rule_ids = {finding["rule_id"] for finding in body["findings"]}
        assert "security.unsafe-call" in rule_ids
        assert "bug.null-dereference" in rule_ids
        assert "quality.unreachable-code" in rule_ids
        assert "security.tainted-command" in rule_ids
        assert "analysis.path-feasible" in rule_ids

        listed = client.get("/api/analyses")
        assert listed.status_code == 200
        assert listed.json()[0]["finding_count"] >= 5

        paged = client.get("/api/analyses?limit=1&offset=0")
        assert paged.status_code == 200
        assert len(paged.json()) == 1

        findings = client.get(f"/api/analyses/{body['id']}/findings?severity=high")
        assert findings.status_code == 200
        assert all(finding["severity"] == "high" for finding in findings.json())

        one_finding = client.get(f"/api/analyses/{body['id']}/findings?limit=1")
        assert one_finding.status_code == 200
        assert len(one_finding.json()) == 1

        updated = client.patch(
            f"/api/analyses/{body['id']}",
            json={"project_name": "renamed-sample"},
        )
        assert updated.status_code == 200
        assert updated.json()["project_name"] == "renamed-sample"

        bad_update = client.patch(f"/api/analyses/{body['id']}", json={"rule_pack": "missing"})
        assert bad_update.status_code == 400

        status_update = client.patch(f"/api/analyses/{body['id']}", json={"status": "completed"})
        assert status_update.status_code == 200

        rerun = client.post(f"/api/analyses/{body['id']}/rerun")
        assert rerun.status_code == 200
        assert rerun.json()["status"] == "failed"

        sarif = client.get(f"/api/analyses/{body['id']}/sarif")
        assert sarif.status_code == 200
        assert sarif.json()["version"] == "2.1.0"

        deleted = client.delete(f"/api/analyses/{body['id']}")
        assert deleted.status_code == 204

        missing = client.get(f"/api/analyses/{body['id']}")
        assert missing.status_code == 404


def test_file_based_analysis(tmp_path) -> None:
    source = tmp_path / "unsafe.cpp"
    source.write_text("int main(){ char* b = NULL; gets(b); return 0; }", encoding="utf-8")

    with TestClient(create_app()) as client:
        response = client.post(
            "/api/analyses",
            json={"project_name": "file", "source_path": str(source)},
        )

        assert response.status_code == 201
        body = response.json()
        assert body["status"] == "completed"
        assert any(finding["rule_id"] == "security.unsafe-call" for finding in body["findings"])


def test_failed_analysis_for_missing_source_path() -> None:
    with TestClient(create_app()) as client:
        response = client.post(
            "/api/analyses",
            json={"project_name": "missing", "source_path": "does-not-exist.cpp"},
        )

        assert response.status_code == 201
        body = response.json()
        assert body["status"] == "failed"
        assert "not found" in body["error_message"].lower()


def test_invalid_requests_return_400() -> None:
    with TestClient(create_app()) as client:
        bad_payload = client.post(
            "/api/analyses",
            json={"project_name": "", "source_path": "sample.cpp", "source_code": "int main(){}"},
        )
        assert bad_payload.status_code == 400
        assert bad_payload.json()["detail"] == "Request validation failed"

        bad_rule_pack = client.post(
            "/api/analyses",
            json={
                "project_name": "bad-rule",
                "source_path": "sample.cpp",
                "source_code": "int main(){}",
                "rule_pack": "missing",
            },
        )
        assert bad_rule_pack.status_code == 400
        assert "missing" in bad_rule_pack.json()["detail"]

        bad_page = client.get("/api/analyses?limit=1000")
        assert bad_page.status_code == 400


def test_not_found_responses() -> None:
    with TestClient(create_app()) as client:
        assert client.get(f"/api/analyses/{MISSING_ID}").status_code == 404
        missing_update = client.patch(
            f"/api/analyses/{MISSING_ID}",
            json={"project_name": "x"},
        )
        assert missing_update.status_code == 404
        assert client.post(f"/api/analyses/{MISSING_ID}/rerun").status_code == 404
        assert client.get(f"/api/analyses/{MISSING_ID}/findings").status_code == 404
        assert client.get(f"/api/analyses/{MISSING_ID}/sarif").status_code == 404
        assert client.delete(f"/api/analyses/{MISSING_ID}").status_code == 404
