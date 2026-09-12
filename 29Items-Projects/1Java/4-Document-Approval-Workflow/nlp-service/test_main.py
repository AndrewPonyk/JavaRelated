import unittest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


class TestNlpService(unittest.TestCase):

    def test_health_endpoint(self):
        response = client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "UP")

    def test_legal_urgent_classification(self):
        payload = {
            "title": "Immediate lawsuit and contract penalty breach",
            "content": "A serious legal dispute has arisen regarding regulatory compliance and financial liability penalties."
        }
        response = client.post("/analyze", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertEqual(data["category"], "LEGAL_RISK")
        self.assertEqual(data["recommended_role"], "LEGAL_COUNSEL")
        self.assertGreaterEqual(data["urgency_score"], 0.7)
        self.assertIn("CRITICAL", [data["polarity"], "CRITICAL", "NEGATIVE"])

    def test_financial_procurement_classification(self):
        payload = {
            "title": "Quarterly Vendor Procurement Budget",
            "content": "Review invoice expenditure and total cost of $50,000 for infrastructure."
        }
        response = client.post("/analyze", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertEqual(data["category"], "FINANCIAL")
        self.assertEqual(data["recommended_role"], "FINANCE_CONTROLLER")

    def test_general_document_classification(self):
        payload = {
            "title": "Team weekly update notes",
            "content": "Standard operational sync and task backlog review for the sprint."
        }
        response = client.post("/analyze", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertEqual(data["category"], "GENERAL")
        self.assertEqual(data["recommended_role"], "TEAM_LEAD")


if __name__ == "__main__":
    unittest.main()
