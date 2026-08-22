from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import firebase_admin
from firebase_admin import firestore, messaging

app = FastAPI(title="Umbrella Cloud Relay")

# Initialize Firebase Admin SDK
# Assumes GOOGLE_APPLICATION_CREDENTIALS environment variable is set in Cloud Run
if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()

class RulesPayload(BaseModel):
    family_id: str
    encrypted_rules: str
    kid_fcm_token: str

class LogsPayload(BaseModel):
    family_id: str
    encrypted_logs: str

@app.post("/sync/rules")
async def sync_rules(payload: RulesPayload):
    try:
        # Save to Firestore: families/{family_id}/rules
        doc_ref = db.collection("families").document(payload.family_id).collection("rules").document("latest")
        doc_ref.set({"data": payload.encrypted_rules})

        # Send silent push notification via FCM to wake up Kid app
        message = messaging.Message(
            data={
                "action": "FETCH_RULES"
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
        # Save to Firestore: families/{family_id}/logs
        db.collection("families").document(payload.family_id).collection("logs").add({
            "data": payload.encrypted_logs,
            "timestamp": firestore.SERVER_TIMESTAMP
        })
        return {"status": "success", "message": "Logs saved securely"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
