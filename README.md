# ☂️ Umbrella: AI-Powered Parental Control

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Gemma 4 API](https://img.shields.io/badge/Gemma_4_API-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white)](#)
[![Cloud Run](https://img.shields.io/badge/Cloud_Run-Cloud-black?style=for-the-badge&logo=googlecloud&logoColor=white)](#)

**Umbrella** is a next-generation parental control system powered by the **Gemma 4 API**. 

Built for Android, Umbrella replaces static blocklists with contextual AI reasoning. By offloading the heavy lifting to the cloud, Umbrella provides deep screen analysis and rule enforcement without draining the child's battery or requiring an expensive flagship phone. 

---

## ✨ Core Philosophy & Features

*   **Contextual SLM (Gemma 4 API):** Standard apps block domains statically. Umbrella uses AI to read the *context* of a screen. (e.g., *Is this a harmless biology video or inappropriate content?*)
*   **Ultra-Lightweight Client:** By using a Cloud Proxy architecture, the kid's device acts only as a sensor and enforcer, resulting in near-zero battery drain.
*   **Smart Power Management:** Screen text extraction and API calls happen on a strict 5-minute interval. All processes pause completely when the screen turns off.
*   **Dual-Layer Enforcement:**
    *   **Local DNS VPN:** Silently intercepts requests to force SafeSearch and block known forbidden domains at the network level.
    *   **Accessibility Services:** Scrapes screen text and instantly draws un-closeable blocks if explicit content, cyberbullying, or app tampering is detected.

---

## 🏗️ Architecture & Data Flow

Umbrella is split into three components: **Parent Command Center**, **Cloud Relay (Gemma Proxy)**, and the **Kid Enforcement Engine**. 

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
        
        P_UI -- "Block gaming today" --> P_Rules
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
    FastAPI -- Prompts AI --> GemmaAPI
    GemmaAPI -- Returns JSON Verdict --> FastAPI
    
    FastAPI -- Stores Logs & Rules --> Firestore
    FastAPI -- Triggers --> FCM
    
    FastAPI -- Returns {"action": "BLOCK"} --> Enforcer
    Enforcer -- Blocks Screen --> A11y
    Enforcer -- Injects SafeSearch --> VPN

    class P_UI,P_Rules parent;
    class K_FCM,VPN,A11y,Enforcer kid;
    class FastAPI,Firestore,FCM cloud;
    class GemmaAPI ai;
