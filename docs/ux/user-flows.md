# AI Production Support Platform — User Flows

This document defines the main task flows for the production support console.

---

# Flow 1 — Sign In and Authorization

## Goal
Enter the platform and land on the highest-value view allowed by role and application/environment access.

```mermaid
flowchart TD
    A[Open Support Console] --> B{Authenticated?}
    B -- No --> C[OIDC Login]
    C --> D{Authentication Success?}
    D -- No --> E[Show Login Error]
    E --> C
    D -- Yes --> F[Load Roles + App/Environment Access]
    B -- Yes --> F
    F --> G{Has Console Access?}
    G -- No --> H[Access Denied]
    G -- Yes --> I[Open Overview Dashboard]
```

Error states:
- identity provider unavailable;
- authenticated but no application access;
- session expired.

---

# Flow 2 — Dashboard Triage

## Goal
Identify which production issue needs attention first.

```mermaid
flowchart TD
    A[Overview Dashboard] --> B[Review Critical Incidents]
    B --> C{Critical or SLA Risk?}
    C -- Yes --> D[Open Incident]
    C -- No --> E[Review Degraded Applications]
    E --> F{Degradation Found?}
    F -- Yes --> G[Open Application Health]
    F -- No --> H[Review Recent Alerts]
    G --> I[Start Investigation]
    D --> I
    H --> J{Needs Incident?}
    J -- Yes --> K[Create/Open Correlated Incident]
    J -- No --> L[Continue Monitoring]
```

---

# Flow 3 — Alert to Correlated Incident

## Goal
Turn multiple related signals into one operational incident.

```mermaid
flowchart TD
    A[Prometheus / Logs / Traces / Kafka / DB Signal] --> B[Normalize Signal]
    B --> C[Correlation + Fingerprint]
    C --> D{Matching Active Incident?}
    D -- Yes --> E[Attach New Evidence]
    D -- No --> F{Incident Threshold Met?}
    F -- No --> G[Keep Signal / Alert Only]
    F -- Yes --> H[Create Incident]
    H --> I[Calculate Severity + SLA]
    I --> J[Notify Support Queue]
    E --> K[Update Timeline]
    J --> K
```

---

# Flow 4 — Acknowledge and Assign Incident

## Goal
Establish ownership quickly.

```mermaid
flowchart TD
    A[Open Incident Detail] --> B{Already Acknowledged?}
    B -- No --> C[Acknowledge]
    C --> D[Record User + Time]
    B -- Yes --> E[Review Current Owner]
    D --> E
    E --> F{Owner Assigned?}
    F -- No --> G[Assign Engineer/Team]
    F -- Yes --> H[Continue Investigation]
    G --> H
    H --> I[Status = Investigating]
```

Feedback:
- show SLA timer;
- show assignment confirmation;
- update activity timeline immediately.

---

# Flow 5 — AI-Guided Investigation

## Goal
Use AI to select relevant diagnostic tools and explain evidence.

```mermaid
flowchart TD
    A[Open Investigation Workspace] --> B[Choose App + Environment + Time Window]
    B --> C[Ask Question]
    C --> D[Validate Access + Registered Application]
    D --> E{Valid?}
    E -- No --> F[Explain Validation Failure]
    E -- Yes --> G[AI Chooses Approved Read-Only Tools]
    G --> H[Execute Tools Within Budget]
    H --> I[Collect Metrics / Logs / Traces / Kafka / DB / Knowledge]
    I --> J{Enough Evidence?}
    J -- No --> K[Return Partial Findings + Missing Evidence]
    J -- Yes --> L[Generate Grounded Explanation]
    K --> M[Suggest Next Safe Checks]
    L --> M
    M --> N[Engineer Reviews Evidence]
    N --> O{Attach to Incident?}
    O -- Yes --> P[Save Investigation + Evidence]
    O -- No --> Q[Keep Investigation Session]
```

---

# Flow 6 — Drill Down from AI Statement to Evidence

## Goal
Verify why an AI conclusion was made.

```mermaid
flowchart TD
    A[AI Finding] --> B[Select Evidence Badge]
    B --> C{Evidence Type}
    C -->|Metric| D[Open Metric Chart at Time Window]
    C -->|Log| E[Open Log Event/Pattern]
    C -->|Trace| F[Open Trace Detail]
    C -->|Kafka| G[Open Consumer/Partition Evidence]
    C -->|Database| H[Open DB Diagnostic Evidence]
    C -->|Runbook| I[Open Knowledge Source]
    D --> J[Return to Investigation]
    E --> J
    F --> J
    G --> J
    H --> J
    I --> J
```

---

# Flow 7 — Metrics Investigation

```mermaid
flowchart TD
    A[Metrics Page] --> B[Select App + Environment]
    B --> C[Select Time Window]
    C --> D[View Golden Signals]
    D --> E{Anomaly?}
    E -- No --> F[Adjust Time/Filter]
    E -- Yes --> G[Inspect Metric Detail]
    G --> H[Open Related Alerts]
    H --> I{Need Cross-System Investigation?}
    I -- Yes --> J[Start AI Investigation with Context]
    I -- No --> K[Save/Share Evidence]
```

---

# Flow 8 — Log Investigation

```mermaid
flowchart TD
    A[Logs Page] --> B[Select App + Environment + Time]
    B --> C[Search Errors / Correlation ID]
    C --> D{Results?}
    D -- No --> E[Expand Time Window / Filters]
    D -- Yes --> F[View Error Pattern Summary]
    F --> G[Open Event Timeline]
    G --> H{Need Trace/Kafka/DB Context?}
    H -- Yes --> I[Launch Cross-System Investigation]
    H -- No --> J[Attach Log Evidence]
```

---

# Flow 9 — Trace Investigation

```mermaid
flowchart TD
    A[Traces Page] --> B[Search Trace ID / Slow Spans]
    B --> C[Open Trace]
    C --> D[Inspect Service Path]
    D --> E{Slow/Error Span?}
    E -- No --> F[Return to Search]
    E -- Yes --> G[Open Span Detail]
    G --> H[View Related Logs/Metrics]
    H --> I[Attach Evidence or Start Investigation]
```

---

# Flow 10 — Kafka Investigation

```mermaid
flowchart TD
    A[Kafka Page] --> B[Select Application]
    B --> C[View Cluster/Topic/Consumer Group]
    C --> D{High Lag?}
    D -- No --> E[Monitor]
    D -- Yes --> F[Open Partition Lag Detail]
    F --> G[Check Consumer Status + Offsets]
    G --> H[Compare With Logs/Metrics]
    H --> I[Attach Evidence / Start AI Investigation]
```

---

# Flow 11 — Database Investigation

```mermaid
flowchart TD
    A[Database Page] --> B[Select Application]
    B --> C[View DB Health + Pool]
    C --> D{Pool Pressure or DB Failure?}
    D -- No --> E[Review Activity]
    D -- Yes --> F[Inspect Pool/Connection Evidence]
    F --> G[Review DB Activity]
    G --> H[Correlate With Traces/Logs]
    H --> I[Attach Evidence / Run Runbook]
```

---

# Flow 12 — Knowledge / Runbook Search

```mermaid
flowchart TD
    A[Knowledge] --> B[Search Question / Error]
    B --> C[Semantic Retrieval]
    C --> D{Relevant Sources?}
    D -- No --> E[Show No Reliable Match]
    D -- Yes --> F[Show Ranked Sources]
    F --> G[Open Source]
    F --> H[Ask Knowledge Question]
    H --> I[Answer With Source Citations]
    I --> J{Use in Incident?}
    J -- Yes --> K[Attach Runbook/Source]
    J -- No --> L[Return to Knowledge]
```

---

# Flow 13 — Incident Escalation

```mermaid
flowchart TD
    A[Incident Active] --> B{SLA Risk/Breach?}
    B -- No --> C[Continue Investigation]
    B -- Yes --> D[Show Escalation Banner]
    D --> E[Notify Lead / On-call]
    E --> F[Record Escalation Event]
    F --> G{Owner Responded?}
    G -- Yes --> H[Continue Investigation]
    G -- No --> I[Escalate Next Level]
```

---

# Flow 14 — Resolve Incident

```mermaid
flowchart TD
    A[Incident Investigating] --> B[Mitigation Applied]
    B --> C[Observe Recovery Evidence]
    C --> D{Service Stable?}
    D -- No --> E[Return to Investigation]
    D -- Yes --> F[Enter Resolution Summary]
    F --> G[Select Root Cause Category]
    G --> H[Attach Final Evidence]
    H --> I[Resolve Incident]
    I --> J[Record Resolved Time + User]
    J --> K[Create Postmortem Draft]
```

Critical UX rule: resolving an incident is a human decision. AI may propose wording but must not auto-resolve.

---

# Flow 15 — Postmortem Review

```mermaid
flowchart TD
    A[Resolved Incident] --> B[Generate Draft Timeline]
    B --> C[Collect Evidence + Investigation Summary]
    C --> D[AI Drafts Postmortem Sections]
    D --> E[Human Reviews/Edit]
    E --> F{Approved?}
    F -- No --> E
    F -- Yes --> G[Publish Postmortem]
    G --> H[Index as Knowledge]
    H --> I[Available for Similar Incident Search]
```

---

# Flow 16 — Application Onboarding

```mermaid
flowchart TD
    A[Applications] --> B[Add Application]
    B --> C[Identity + Environment]
    C --> D[Diagnostic Endpoint]
    D --> E[Configure Optional Data Sources]
    E --> F[Kafka]
    E --> G[Database]
    E --> H[Logging]
    E --> I[Tracing]
    E --> J[Metrics]
    F --> K[Test Connections]
    G --> K
    H --> K
    I --> K
    J --> K
    K --> L{Required Checks Pass?}
    L -- No --> M[Show Per-Integration Error]
    M --> E
    L -- Yes --> N[Configure Access Policy]
    N --> O[Review]
    O --> P[Activate Application]
```

---

# Flow 17 — Investigation Feedback

```mermaid
flowchart TD
    A[Investigation Answer] --> B{Helpful?}
    B -- Yes --> C[Mark Helpful]
    B -- No --> D[Choose Reason]
    D --> E[Incorrect / Missing Evidence / Not Helpful]
    E --> F[Optional Comment]
    C --> G[Persist Feedback]
    F --> G
    G --> H[Use in Quality Reporting]
```

---

# Flow 18 — Degraded Dependency Experience

## Goal
Avoid presenting infrastructure outage as a false "healthy" result.

```mermaid
flowchart TD
    A[Run Investigation] --> B[Call Diagnostic Sources]
    B --> C{One Source Unavailable?}
    C -- No --> D[Normal Evidence Result]
    C -- Yes --> E[Mark Source Unavailable]
    E --> F[Continue With Other Sources]
    F --> G[Show Partial Evidence Banner]
    G --> H[Explain Missing Source]
    H --> I[Offer Retry]
```

The interface must distinguish:
- no matching evidence;
- source returned zero results;
- source unavailable;
- user lacks permission.
