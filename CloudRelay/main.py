from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Dict, Any, List
import firebase_admin
from firebase_admin import firestore, messaging
import vertexai
from vertexai.generative_models import GenerativeModel, GenerationConfig
import os
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("umbrella-relay")

app = FastAPI(title="Umbrella Cloud Relay", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── Firebase Init ────────────────────────────────────────────────────────────

if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()

# ─── Vertex AI / Gemma 4 Init ────────────────────────────────────────────────

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "your-project-id")
LOCATION = os.environ.get("VERTEX_LOCATION", "us-central1")
GEMMA_MODEL = "gemma-4-9b-it"  # Gemma 4 via Vertex AI

vertexai.init(project=PROJECT_ID, location=LOCATION)
gemma_model = GenerativeModel(GEMMA_MODEL)

# ─── Request / Response Models ────────────────────────────────────────────────

class RulesPayload(BaseModel):
    family_id: str
    encrypted_rules: str
    kid_fcm_token: str
    blocked_domains: Optional[List[str]] = []

class LogsPayload(BaseModel):
    family_id: str
    encrypted_logs: str

class EvaluateRequest(BaseModel):
    family_id: str
    screen_text: str
    active_package: Optional[str] = ""

class EvaluateResponse(BaseModel):
    verdict: str          # "BLOCK" or "ALLOW"
    reason: Optional[str] = None

class RulesResponse(BaseModel):
    rules: Optional[Dict[str, Any]] = None
    blocked_domains: Optional[List[str]] = []

# ─── Helper: Build Gemma evaluation prompt ───────────────────────────────────

def build_eval_prompt(screen_text: str, active_package: str, rule_dict: dict) -> str:
    rules_str = json.dumps(rule_dict, indent=2)
    return f"""You are Umbrella, a child safety AI. You evaluate screen content against parental rules.

ACTIVE APP: {active_package}

PARENTAL RULES (JSON):
{rules_str}

SCREEN TEXT:
{screen_text[:2000]}

Respond ONLY with a JSON object with two fields:
- "verdict": either "BLOCK" or "ALLOW"
- "reason": a brief, parent-readable explanation (1 sentence max)

Example: {{"verdict": "BLOCK", "reason": "Screen contains explicit content violating the no-adult-content rule."}}

Your response:"""

# ─── Endpoints ───────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0.0"}


@app.post("/api/evaluate", response_model=EvaluateResponse)
async def evaluate(payload: EvaluateRequest):
    """
    Core AI evaluation endpoint. Called every 5 minutes by the kid's device.
    Fetches the family's active rules from Firestore, then asks Gemma 4
    to evaluate the screen text against those rules.
    """
    try:
        # 1. Fetch active rules for this family
        rules_doc = (
            db.collection("families")
            .document(payload.family_id)
            .collection("rules")
            .document("latest")
            .get()
        )

        if not rules_doc.exists:
            logger.info(f"No rules for family={payload.family_id} — allowing by default.")
            return EvaluateResponse(verdict="ALLOW", reason="No active rules configured.")

        rule_data = rules_doc.to_dict() or {}
        rule_dict = rule_data.get("rules", {})

        # 2. Build and send prompt to Gemma 4
        prompt = build_eval_prompt(
            screen_text=payload.screen_text,
            active_package=payload.active_package or "",
            rule_dict=rule_dict
        )

        response = gemma_model.generate_content(
            prompt,
            generation_config=GenerationConfig(
                max_output_tokens=128,
                temperature=0.0,  # Deterministic for safety decisions
            )
        )

        raw_text = response.text.strip()
        logger.info(f"Gemma response for family={payload.family_id}: {raw_text}")

        # 3. Parse JSON response from Gemma
        # Strip markdown code fences if present
        if raw_text.startswith("```"):
            raw_text = raw_text.split("```")[1]
            if raw_text.startswith("json"):
                raw_text = raw_text[4:]

        result = json.loads(raw_text)
        verdict = result.get("verdict", "ALLOW").upper()
        reason = result.get("reason", "")

        if verdict not in ("BLOCK", "ALLOW"):
            verdict = "ALLOW"

        # 4. Log to Firestore if blocked
        if verdict == "BLOCK":
            db.collection("families").document(payload.family_id).collection("logs").add({
                "verdict": verdict,
                "reason": reason,
                "active_package": payload.active_package,
                "screen_snippet": payload.screen_text[:200],
                "timestamp": firestore.SERVER_TIMESTAMP,
            })
            logger.info(f"BLOCKED family={payload.family_id} pkg={payload.active_package}: {reason}")

        return EvaluateResponse(verdict=verdict, reason=reason)

    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse Gemma response: {e}")
        # Fail safe — allow on parse error
        return EvaluateResponse(verdict="ALLOW", reason="AI parse error — allowing.")
    except Exception as e:
        logger.error(f"Evaluate error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/rules/{family_id}", response_model=RulesResponse)
async def get_rules(family_id: str):
    """
    Called by the kid's device on FCM wake-up to fetch latest parental rules.
    Returns structured rules + domain blocklist.
    """
    try:
        doc = (
            db.collection("families")
            .document(family_id)
            .collection("rules")
            .document("latest")
            .get()
        )

        if not doc.exists:
            return RulesResponse(rules={}, blocked_domains=[])

        data = doc.to_dict() or {}
        return RulesResponse(
            rules=data.get("rules"),
            blocked_domains=data.get("blocked_domains", [])
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/sync/rules")
async def sync_rules(payload: RulesPayload):
    """
    Called by the Parent app when new rules are set via the AI chat.
    Saves to Firestore and sends a silent FCM wake-up to the kid's device.
    """
    try:
        doc_ref = (
            db.collection("families")
            .document(payload.family_id)
            .collection("rules")
            .document("latest")
        )
        doc_ref.set({
            "data": payload.encrypted_rules,
            "blocked_domains": payload.blocked_domains or [],
        }, merge=True)

        message = messaging.Message(
            data={"action": "FETCH_RULES"},
            token=payload.kid_fcm_token,
            android=messaging.AndroidConfig(priority="high"),
        )
        messaging.send(message)

        return {"status": "success", "message": "Rules synced and wake-up signal sent."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/sync/logs")
async def sync_logs(payload: LogsPayload):
    """Called by the kid's device to push encrypted activity logs to the parent."""
    try:
        db.collection("families").document(payload.family_id).collection("logs").add({
            "data": payload.encrypted_logs,
            "timestamp": firestore.SERVER_TIMESTAMP,
        })
        return {"status": "success", "message": "Logs saved securely."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
