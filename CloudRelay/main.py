import json
import os
from typing import Any, Dict, List, Optional

import firebase_admin
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from firebase_admin import firestore, messaging
from pydantic import BaseModel, Field

app = FastAPI(title="Umbrella Cloud Relay")

# Enable CORS for all origins
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static files directory for frontend
static_dir = os.path.join(os.path.dirname(__file__), "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

# Initialize Firebase Admin SDK
# Assumes GOOGLE_APPLICATION_CREDENTIALS environment variable is set in Cloud Run
db = None
try:
    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    db = firestore.client()
except Exception as e:
    print(f"Firebase Admin not initialized (running in local development mode): {e}")

# ==================== Data Models ====================

class AppRule(BaseModel):
    package_name: str
    app_name: str
    is_blocked: bool = False
    daily_limit_minutes: Optional[int] = None

class RuleDict(BaseModel):
    blocked_categories: List[str] = Field(
        default_factory=list,
        description="Categories to block e.g., GAMING, SOCIAL_MEDIA, ADULT, VIOLENCE, GAMBLING, STREAMING"
    )
    blocked_keywords: List[str] = Field(
        default_factory=list,
        description="Keywords or phrases that trigger an instant screen overlay warning"
    )
    app_rules: List[AppRule] = Field(
        default_factory=list,
        description="Specific per-app blocking or screen time limits"
    )
    bedtime_start: Optional[str] = Field(
        None,
        description="HH:MM format for nighttime lock e.g. 21:00"
    )
    bedtime_end: Optional[str] = Field(
        None,
        description="HH:MM format for nighttime unlock e.g. 07:00"
    )
    school_hours_start: Optional[str] = Field(
        None,
        description="HH:MM format for school curfew start e.g. 08:30"
    )
    school_hours_end: Optional[str] = Field(
        None,
        description="HH:MM format for school curfew end e.g. 15:30"
    )
    safe_search_forced: bool = True
    raw_intent: str = ""
    explanation: str = ""

class GenerateRulesRequest(BaseModel):
    family_id: str
    prompt: str
    existing_rules: Optional[Dict[str, Any]] = None

class GenerateRulesResponse(BaseModel):
    status: str
    family_id: str
    rule_dict: RuleDict
    explanation: str
    raw_json: str

class RulesPayload(BaseModel):
    family_id: str
    encrypted_rules: str
    kid_fcm_token: Optional[str] = None

class LogsPayload(BaseModel):
    family_id: str
    encrypted_logs: Optional[str] = None
    app_name: Optional[str] = "Unknown"
    package_name: Optional[str] = None
    severity: Optional[str] = "INFO" # INFO, WARNING, BLOCKED
    category: Optional[str] = "GENERAL"
    screen_snippet: Optional[str] = None
    ai_reasoning: Optional[str] = None
    action_taken: Optional[str] = "MONITORED" # MONITORED, OVERLAY_BLOCKED, DNS_BLOCKED

# ==================== Gemma 4 / AI Inference ====================

def call_gemma_rule_generator(prompt: str, existing_rules: Optional[Dict[str, Any]] = None) -> RuleDict:
    """
    Translates natural language parental requirements into a structured RuleDict
    using Gemma 4 API (Vertex AI / Google GenAI SDK).
    Fallback logic provides robust deterministic parsing if API key is not configured in local dev.
    """
    api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
    
    system_instruction = (
        "You are Gemma 4, an advanced SLM for Umbrella Parental Control. "
        "Your role is to translate a parent's natural language command into a structured JSON RuleDict.\n"
        "Output ONLY a valid JSON object matching this schema:\n"
        "{\n"
        '  "blocked_categories": ["GAMING", "SOCIAL_MEDIA", "ADULT", "VIOLENCE", "GAMBLING", "STREAMING"],\n'
        '  "blocked_keywords": ["str"],\n'
        '  "app_rules": [{"package_name": "str", "app_name": "str", "is_blocked": bool, "daily_limit_minutes": int}],\n'
        '  "bedtime_start": "HH:MM",\n'
        '  "bedtime_end": "HH:MM",\n'
        '  "school_hours_start": "HH:MM",\n'
        '  "school_hours_end": "HH:MM",\n'
        '  "safe_search_forced": true,\n'
        '  "explanation": "Human-readable summary of what was configured for the parent"\n'
        "}"
    )

    if api_key:
        try:
            from google import genai
            from google.genai import types

            client = genai.Client(api_key=api_key)
            response = client.models.generate_content(
                model='gemini-2.5-flash', # or gemma-4 endpoint
                contents=f"Parent Command: '{prompt}'. Existing rules: {json.dumps(existing_rules or {})}",
                config=types.GenerateContentConfig(
                    system_instruction=system_instruction,
                    response_mime_type="application/json",
                    temperature=0.2
                )
            )
            parsed_data = json.loads(response.text)
            parsed_data["raw_intent"] = prompt
            return RuleDict(**parsed_data)
        except Exception as e:
            print(f"Gemma API Call error: {e}, falling back to intelligent rule parser")

    # Intelligent deterministic fallback when offline or no API key
    lower = prompt.lower()
    categories = []
    keywords = []
    app_rules = []
    bedtime_start = None
    bedtime_end = None
    school_start = None
    school_end = None
    explanation_parts = []

    if "game" in lower or "gaming" in lower or "roblox" in lower or "minecraft" in lower:
        categories.append("GAMING")
        keywords.extend(["roblox", "minecraft", "fortnite", "steam", "play game"])
        app_rules.append(AppRule(package_name="com.roblox.client", app_name="Roblox", is_blocked=True))
        app_rules.append(AppRule(package_name="com.mojang.minecraftpe", app_name="Minecraft", is_blocked=True))
        explanation_parts.append("Blocked all gaming apps and keywords (Roblox, Minecraft, etc.)")

    if "social" in lower or "tiktok" in lower or "instagram" in lower or "snapchat" in lower:
        categories.append("SOCIAL_MEDIA")
        keywords.extend(["tiktok", "reels", "shorts", "snapchat", "instagram"])
        app_rules.append(AppRule(package_name="com.zhiliaoapp.musically", app_name="TikTok", is_blocked=True))
        app_rules.append(AppRule(package_name="com.instagram.android", app_name="Instagram", is_blocked=True))
        app_rules.append(AppRule(package_name="com.snapchat.android", app_name="Snapchat", is_blocked=True))
        explanation_parts.append("Restricted social media platforms (TikTok, Instagram, Snapchat)")

    if "bed" in lower or "night" in lower or "sleep" in lower or "curfew" in lower:
        bedtime_start = "21:00"
        bedtime_end = "07:00"
        explanation_parts.append("Set Bedtime Curfew from 9:00 PM to 7:00 AM")

    if "school" in lower or "class" in lower or "study" in lower:
        school_start = "08:30"
        school_end = "15:30"
        explanation_parts.append("Enforced School Hours study focus mode (8:30 AM - 3:30 PM)")

    if "adult" in lower or "nsfw" in lower or "mature" in lower or "safe" in lower:
        categories.append("ADULT")
        categories.append("VIOLENCE")
        explanation_parts.append("Forced Strict SafeSearch and blocked adult/mature categories")

    if not explanation_parts:
        categories.append("GENERAL_RESTRICTION")
        keywords.append(prompt)
        explanation_parts.append(f"Configured smart contextual filter for '{prompt}'")

    return RuleDict(
        blocked_categories=categories,
        blocked_keywords=keywords,
        app_rules=app_rules,
        bedtime_start=bedtime_start,
        bedtime_end=bedtime_end,
        school_hours_start=school_start,
        school_hours_end=school_end,
        safe_search_forced=True,
        raw_intent=prompt,
        explanation="; ".join(explanation_parts)
    )

# ==================== API Endpoints ====================

@app.get("/", response_class=HTMLResponse)
async def serve_frontend():
    index_path = os.path.join(static_dir, "index.html")
    if os.path.exists(index_path):
        with open(index_path, "r", encoding="utf-8") as f:
            return HTMLResponse(content=f.read())
    return HTMLResponse("<h1>Umbrella Cloud Relay</h1><p>Frontend file not found.</p>")

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "Umbrella Cloud Relay",
        "firestore_connected": db is not None
    }

@app.post("/api/generate-rules", response_model=GenerateRulesResponse)
async def generate_rules(req: GenerateRulesRequest):
    """
    Accepts parent's natural language input, runs Gemma 4 reasoning,
    and returns a structured RuleDict.
    """
    try:
        rule_dict = call_gemma_rule_generator(req.prompt, req.existing_rules)
        raw_json_str = rule_dict.model_dump_json(indent=2)
        
        if db is not None:
            try:
                db.collection("families").document(req.family_id).collection("rules").document("draft").set({
                    "rule_dict": json.loads(raw_json_str),
                    "updated_at": firestore.SERVER_TIMESTAMP
                })
            except Exception as e:
                print(f"Firestore draft save skipped: {e}")

        return GenerateRulesResponse(
            status="success",
            family_id=req.family_id,
            rule_dict=rule_dict,
            explanation=rule_dict.explanation,
            raw_json=raw_json_str
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Rule generation failed: {str(e)}")

@app.post("/sync/rules")
async def sync_rules(payload: RulesPayload):
    try:
        if db is not None:
            # Save to Firestore: families/{family_id}/rules/latest
            doc_ref = db.collection("families").document(payload.family_id).collection("rules").document("latest")
            doc_ref.set({
                "data": payload.encrypted_rules,
                "timestamp": firestore.SERVER_TIMESTAMP
            })

            # Send silent push notification via FCM to wake up Kid app if token is provided
            if payload.kid_fcm_token:
                message = messaging.Message(
                    data={
                        "action": "FETCH_RULES",
                        "family_id": payload.family_id
                    },
                    token=payload.kid_fcm_token,
                    android=messaging.AndroidConfig(
                        priority="high"
                    )
                )
                messaging.send(message)
        
        return {"status": "success", "message": "Rules synced and wake-up signal sent"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/sync/logs")
async def sync_logs(payload: LogsPayload):
    try:
        log_id = "mock_log_id"
        if db is not None:
            # Save to Firestore: families/{family_id}/logs
            log_data = {
                "app_name": payload.app_name,
                "package_name": payload.package_name,
                "severity": payload.severity,
                "category": payload.category,
                "screen_snippet": payload.screen_snippet,
                "ai_reasoning": payload.ai_reasoning,
                "action_taken": payload.action_taken,
                "data": payload.encrypted_logs,
                "timestamp": firestore.SERVER_TIMESTAMP
            }
            doc_ref = db.collection("families").document(payload.family_id).collection("logs").add(log_data)
            log_id = doc_ref[1].id
            
        return {"status": "success", "message": "Log saved securely", "log_id": log_id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    print("Starting Umbrella Cloud Relay on http://127.0.0.1:8000 ...")
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)

