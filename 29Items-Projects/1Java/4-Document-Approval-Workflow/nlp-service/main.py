import os
import logging
from typing import List, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import spacy
from spacytextblob.spacytextblob import SpacyTextBlob

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("nlp-service")

app = FastAPI(
    title="Document Approval NLP Microservice",
    description="spaCy-powered sentiment analysis, categorization, and urgency routing for document approval workflows",
    version="1.0.0"
)

# Load spaCy model & attach sentiment extension
try:
    nlp = spacy.load("en_core_web_md")
except Exception:
    logger.warning("en_core_web_md not found, falling back to en_core_web_sm or blank model")
    try:
        nlp = spacy.load("en_core_web_sm")
    except Exception:
        nlp = spacy.blank("en")

if "spacytextblob" not in nlp.pipe_names:
    nlp.add_pipe("spacytextblob")


class AnalyzeRequest(BaseModel):
    title: str = Field(..., description="Document title")
    content: str = Field(..., description="Document content text")
    metadata: Optional[dict] = Field(default_factory=dict, description="Additional context")


class AnalyzeResponse(BaseModel):
    sentiment_score: float = Field(..., description="Polarity score between -1.0 and 1.0")
    polarity: str = Field(..., description="POSITIVE, NEUTRAL, NEGATIVE, or CRITICAL")
    category: str = Field(..., description="Detected category e.g. LEGAL_RISK, FINANCIAL, HR, TECHNICAL, GENERAL")
    urgency_score: float = Field(..., description="Calculated urgency score between 0.0 and 1.0")
    recommended_role: str = Field(..., description="Auto-routing target role e.g. LEGAL_COUNSEL, FINANCE_CONTROLLER, TEAM_LEAD")
    extracted_entities: List[str] = Field(default_factory=list, description="Named entities detected in text")


CATEGORIES_KEYWORDS = {
    "LEGAL_RISK": ["lawsuit", "dispute", "compliance", "regulatory", "penalty", "contract", "liability", "nda", "breach"],
    "FINANCIAL": ["budget", "procurement", "invoice", "cost", "expenditure", "$", "dollar", "payment", "revenue", "quarterly"],
    "HR_POLICY": ["termination", "hiring", "harassment", "leave", "resignation", "salary", "employee", "benefits"],
    "TECHNICAL": ["architecture", "cloud", "migration", "database", "security vulnerability", "deployment", "infrastructure"]
}

URGENCY_KEYWORDS = ["urgent", "asap", "immediate", "critical", "deadline", "emergency", "penalty", "violation"]


@app.get("/health")
def health_check():
    return {"status": "UP", "service": "nlp-routing-microservice"}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze_document(request: AnalyzeRequest):
    """
    Analyzes document text using spaCy and spacytextblob to compute:
    1. Sentiment polarity score
    2. Document category classification
    3. Urgency weighting
    4. Auto-routing role recommendation
    """
    try:
        combined_text = f"{request.title} \n {request.content}".lower()
        doc = nlp(f"{request.title} \n {request.content}")

        # 1. Sentiment Extraction
        polarity_score = getattr(doc._, "polarity", 0.0) or 0.0
        subjectivity_score = getattr(doc._, "subjectivity", 0.0) or 0.0

        # Determine polarity label
        if polarity_score < -0.3:
            polarity_label = "NEGATIVE"
        elif polarity_score > 0.3:
            polarity_label = "POSITIVE"
        else:
            polarity_label = "NEUTRAL"

        # 2. Urgency Calculation
        urgency_hits = sum(1 for kw in URGENCY_KEYWORDS if kw in combined_text)
        base_urgency = min(1.0, urgency_hits * 0.35)
        # Highly negative sentiment elevates urgency
        if polarity_score < -0.4:
            base_urgency = min(1.0, base_urgency + 0.3)
            polarity_label = "CRITICAL"
        urgency_score = round(base_urgency, 2)

        # 3. Categorization
        detected_category = "GENERAL"
        max_matches = 0
        for category, keywords in CATEGORIES_KEYWORDS.items():
            matches = sum(1 for kw in keywords if kw in combined_text)
            if matches > max_matches:
                max_matches = matches
                detected_category = category

        # 4. Role Auto-Routing Recommendation
        if detected_category == "LEGAL_RISK" or urgency_score >= 0.8:
            recommended_role = "LEGAL_COUNSEL"
        elif detected_category == "FINANCIAL":
            recommended_role = "FINANCE_CONTROLLER"
        elif detected_category == "HR_POLICY":
            recommended_role = "DEPARTMENT_HEAD"
        else:
            recommended_role = "TEAM_LEAD"

        # 5. Extract Named Entities
        entities = [f"{ent.text} ({ent.label_})" for ent in doc.ents][:10]

        logger.info("Analyzed document '%s': Category=%s, Polarity=%.2f, Urgency=%.2f -> Role=%s",
                    request.title, detected_category, polarity_score, urgency_score, recommended_role)

        return AnalyzeResponse(
            sentiment_score=round(float(polarity_score), 2),
            polarity=polarity_label,
            category=detected_category,
            urgency_score=urgency_score,
            recommended_role=recommended_role,
            extracted_entities=entities
        )
    except Exception as e:
        logger.error("Error analyzing document text: %s", str(e), exc_info=True)
        raise HTTPException(status_code=500, detail=f"NLP Analysis Failed: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
