# ☂️ Umbrella: AI-Powered Parental Control

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Gemma 4 API](https://img.shields.io/badge/Gemma_4_API-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white)](#)
[![Cloud Run](https://img.shields.io/badge/Cloud_Run-Cloud-black?style=for-the-badge&logo=googlecloud&logoColor=white)](#)

**Umbrella** is a next-generation parental control system powered by the **Gemma 4 API**. 

Built for Android, Umbrella replaces static blocklists with contextual AI reasoning. By offloading the heavy lifting to the cloud, Umbrella provides deep screen analysis and intelligent rule enforcement without draining the child's battery or requiring an expensive flagship phone. 

---

## ✨ Core Philosophy & Features

*   **Contextual SLM (Gemma 4 API):** Traditional apps block entire domains statically. Umbrella uses AI to read the *context* of a screen. (e.g., *Is this a harmless biology video on YouTube or inappropriate content?*)
*   **Ultra-Lightweight Client:** By utilizing a Cloud Proxy architecture, the kid's device acts only as a sensor and enforcer. This results in near-zero battery drain and high performance on budget smartphones.
*   **Smart Power Management:** Screen text extraction and API calls happen on a strict 5-minute interval. All background extraction processes pause completely when the screen turns off.
*   **Dual-Layer Enforcement:**
    *   **Local DNS VPN:** Silently intercepts network requests to force SafeSearch and block known forbidden domains locally.
    *   **Accessibility Services:** Scrapes screen text and instantly draws un-closeable overlays if explicit content, severe cyberbullying, or app tampering is detected.

---

## 🏗️ Architecture & Data Flow

Umbrella is split into three main components: **Parent Command Center**, **Cloud Relay (Gemma Proxy)**, and the **Kid Enforcement Engine**. 

```mermaid
graph TD
    %% Define Styles
    classDef parent fill:#e3f2fd,stroke:#1e88e5,stroke-width:2px;
    classDef kid fill:#fce4ec,stroke:#e91e63,stroke-width:2px;
    classDef cloud fill:#fff3e0,stroke:#f57c00,stroke-width:2px;
    classDef ai fill:#e8f5e9,stroke:#4caf50,stroke-width:2px;
    
    subgraph Parent Device ["📱 Parent Command Center (Android)"]
        P_UI[Jetpack Compose UI]
        P_Rules[Rule Input]
        
        P_UI -- "E.g. Block gaming today" --> P_Rules
        P_Rules -- POST /api/generate-rules --> FastAPI
    end

    subgraph Cloud Relay ["☁️ Cloud Run Backend (FastAPI)"]
        FastAPI[API Proxy & Logic]
        Firestore[(Cloud Firestore)]
        FCM[Firebase Cloud Messaging]
    end
    
    subgraph Gemma AI ["🧠 Google Cloud Vertex AI"]
        GemmaAPI[Gemma 4 API]
    end

    subgraph Kid Device ["👦 Kid Enforcement Engine (Android)"]
        K_FCM[FCM Receiver]
        VPN[Local DNS VPN]
        A11y[Accessibility Service]
        Enforcer[UI Overlay / Blocker]
        
        FCM -. Wakes App for Rule Update .-> K_FCM
        
        A11y -- Scrapes DOM/Text <br/>(Every 5 mins) --> FastAPI
        VPN -- DNS Intercepts --> FastAPI
    end

    %% Cloud Logic Flow
    FastAPI -- Prompts AI for rule translation --> GemmaAPI
    FastAPI -- Prompts AI for content evaluation --> GemmaAPI
    GemmaAPI -- Returns JSON Verdict --> FastAPI
    
    FastAPI -- Stores Logs & Rules --> Firestore
    FastAPI -- Triggers Rule Update --> FCM
    
    FastAPI -- Returns {"action": "BLOCK"} --> Enforcer
    Enforcer -- Blocks Screen via Overlay --> A11y
    Enforcer -- Injects SafeSearch --> VPN

    class P_UI,P_Rules parent;
    class K_FCM,VPN,A11y,Enforcer kid;
    class FastAPI,Firestore,FCM cloud;
    class GemmaAPI ai;
```

---

## 🛠️ Technical Stack

### 1. Android Apps (Frontend: Parent & Kid)
*   **Language & UI:** Kotlin, Jetpack Compose.
*   **Networking:** Retrofit / OkHttp (for communicating with the Cloud Run API).
*   **System APIs (Kid App):** 
    *   `VpnService` (DNS Interception & sinkholing).
    *   `AccessibilityService` (Screen Reading & UI Overlays).
    *   `CameraX` (QR Code Handshake).

### 2. Cloud Relay (Backend & API Proxy)
*   **Compute:** Google Cloud Run (Serverless, scales to zero).
*   **Backend Framework:** Python (FastAPI).
*   **Database:** Firebase Cloud Firestore (NoSQL, storing JSON rules and activity logs).
*   **Push Notifications:** Firebase Cloud Messaging (FCM) for real-time rule syncing.

### 3. AI Intelligence Layer
*   **Model:** Gemma 4 API (via Google Vertex AI).
*   **Purpose:** 
    1. NLP for converting parent intents into structured JSON rules.
    2. Contextual evaluation of the kid's screen data against active rules.

---

## ⚙️ How It Works (The Lifecycle)

1.  **The Handshake:** The parent downloads the app and generates a QR code. The kid's phone scans the QR code, securely linking the devices via a `family_id` stored in Firestore.
2.  **Rule Generation:** The parent types a natural language command *(e.g., "No social media during school hours")*. The Cloud Run backend passes this to the Gemma 4 API, which translates it into a strict JSON `RuleDict` and saves it to Firestore.
3.  **Enforcement (The 5-Minute Loop):** 
    *   While the kid's screen is ON, the `AccessibilityService` extracts the visible text every 5 minutes.
    *   The app sends this text snippet to the Cloud Run `/api/evaluate` endpoint.
    *   The backend queries the Gemma 4 API: *"Does this text violate the active RuleDict?"*
    *   If the API returns a "BLOCK" verdict, the backend replies to the phone, and the `AccessibilityService` instantly draws an un-closeable warning overlay.
4.  **Logging:** The backend securely logs the incident and a summary of the context to Firestore for the parent's dashboard.
5.  **Sleep Mode:** When the screen turns off (`ACTION_SCREEN_OFF`), text extraction and API calls stop completely to conserve battery. Network blocks are maintained locally by the ultra-lightweight VPN.

---

## 🔒 Security & Anti-Tamper Mechanisms

*   **Settings Shield:** If a child attempts to open Android Settings to disable the VPN or Accessibility permissions, Umbrella's AccessibilityService detects the UI state and instantly forces the global "Back" action, requiring a Parent PIN to proceed.
*   **API Security:** The Kid's device never holds the Gemma API keys. All traffic is routed through your FastAPI Cloud Run proxy, ensuring strict rate-limiting and authorization checks.

---

## 📱 Minimum Device Requirements

By offloading AI inference to the cloud, Umbrella is incredibly lightweight and accessible to standard devices.

*   **Operating System:** Android 11+ (API Level 30+). *(Note: iOS is not supported due to system restrictions on background screen reading).*
*   **RAM:** 2GB Minimum.
*   **Processor:** Any modern entry-level or mid-range smartphone processor.
*   **Network:** Requires an active internet connection for the AI contextual evaluation. Local VPN blocklists function completely offline.
