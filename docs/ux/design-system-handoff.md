# AI Production Support Platform — Design System and Developer Handoff

## 1. Design Direction

The product is an enterprise production-operations console. The design should feel:

- calm under pressure;
- dense but readable;
- evidence-oriented;
- predictable;
- technically credible;
- accessible.

Use semantic color only where it communicates state. The interface should not look like a marketing dashboard.

---

## 2. Design Tokens

### Color Tokens

Recommended starting palette. Validate final combinations against WCAG 2.2 AA before implementation.

| Token | Suggested Value | Usage |
|---|---:|---|
| color-bg-app | #F6F8FA | Application background |
| color-bg-surface | #FFFFFF | Cards, panels |
| color-bg-subtle | #F0F3F6 | Secondary surfaces |
| color-border-default | #D8DEE4 | Borders |
| color-text-primary | #1F2328 | Main text |
| color-text-secondary | #59636E | Secondary text |
| color-action-primary | #0969DA | Primary actions/links |
| color-info | #0969DA | Informational state |
| color-success | #1A7F37 | Healthy/success |
| color-warning | #9A6700 | Warning |
| color-danger | #CF222E | Critical/error |
| color-focus | #0969DA | Focus ring |

Do not encode incident severity using color alone.

### Severity Mapping

- SEV1 — danger + "SEV1 Critical"
- SEV2 — warning/danger emphasis + "SEV2 High"
- SEV3 — warning + "SEV3 Medium"
- SEV4 — neutral/info + "SEV4 Low"

### Environment Mapping

- PROD — strong label/border treatment
- UAT — distinct label
- QA — neutral label
- DEV — neutral label
- LOCAL — muted label

Environment styles must remain readable in both light and dark themes.

---

## 3. Typography

Recommended system-friendly stack:

```css
font-family: Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
```

Technical values:

```css
font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
```

Suggested scale:

| Token | Size | Weight | Usage |
|---|---:|---:|---|
| type-page-title | 28px | 650 | Page title |
| type-section-title | 20px | 650 | Major section |
| type-card-title | 16px | 600 | Card title |
| type-body | 14px | 400 | Default dense UI |
| type-body-strong | 14px | 600 | Important labels |
| type-caption | 12px | 400 | Metadata |
| type-code | 13px | 400 | IDs/log snippets |

Use at least 1.4 line-height for body text.

---

## 4. Spacing

Use an 8-point base system with a 4px half-step.

```text
4   xs
8   sm
12  md-compact
16  md
24  lg
32  xl
48  2xl
64  3xl
```

Dense table rows may use 8–12px vertical internal spacing while preserving minimum interactive target size.

---

## 5. Radius and Elevation

| Token | Value |
|---|---|
| radius-sm | 4px |
| radius-md | 8px |
| radius-lg | 12px |
| shadow-panel | subtle 0 1px 2px rgba(...) |
| shadow-overlay | stronger modal/popover shadow |

Operational data panels should rely more on borders than heavy shadows.

---

## 6. Grid

Desktop:
- 12-column grid
- 24px page gutters
- 24px column gap

Wide operations screen:
- maximum useful content width may extend to full viewport for tables/traces.

Do not constrain observability screens to a narrow marketing-site max-width.

---

## 7. Core Components

### Button

Variants:
- Primary
- Secondary
- Danger
- Ghost
- Icon

States:
default, hover, active, focus-visible, disabled, loading.

Danger buttons are reserved for high-impact actions, not routine navigation.

### Input / Select

Support:
- label;
- helper text;
- validation;
- prefix/suffix;
- clear;
- loading;
- disabled;
- read-only.

### Badge

Types:
- severity;
- status;
- environment;
- evidence source;
- health.

Badge text must always be visible.

### KPI Card

Anatomy:
- label;
- current value;
- optional delta;
- optional status;
- click-through.

Do not show a delta without a clear comparison period.

### Data Table

Required:
- sticky header;
- sorting;
- filters;
- search where useful;
- pagination or virtualization;
- row keyboard focus;
- clear empty/error state.

IDs and numerical columns can use monospaced type.

### Evidence Card

Anatomy:
- evidence type icon/label;
- title;
- app/environment;
- timestamp;
- value/summary;
- source health;
- deep-link action.

### AI Response Card

Sections:
1. Summary
2. Observed Facts
3. AI Interpretation
4. Missing Evidence
5. Suggested Next Checks
6. Sources/Tools

Use different visual containers for facts and interpretation.

### Incident Timeline Item

Types:
- Alert
- Metric
- Log
- Trace
- Kafka
- Database
- AI
- Human action
- Status change
- Notification

Every item includes timestamp and source.

### Modal

Use only for:
- confirmations;
- short edit forms;
- high-impact operational decisions.

Do not place complex investigations inside modals.

### Drawer

Good for:
- evidence detail;
- filter panels;
- context panel on narrower screens.

---

## 8. Page Templates

### List Page
Header → filters → table → pagination.

### Detail Page
Header/context → tabs → primary content → optional right action rail.

### Investigation Page
Context panel → conversation → evidence panel.

### Observability Page
Shared filter bar → overview → chart/table → drill-down drawer.

### Admin Page
Settings navigation → form/table → audit context.

---

## 9. State Design

Every major component must define:

- loading;
- empty;
- error;
- permission denied;
- partial data;
- stale data;
- success.

### Partial Data Pattern

Example:

```text
Partial evidence
Prometheus and logs responded successfully.
Jaeger is currently unavailable.
Last retry: 14:42:11
[Retry trace source]
```

Do not replace partial content with a full-page generic error.

---

## 10. Motion

Keep motion minimal.

Recommended:
- hover/focus feedback: 100–150ms;
- drawer/modal transition: 180–220ms;
- no decorative looping animation;
- respect prefers-reduced-motion.

Incident severity should never blink or pulse continuously.

---

## 11. Accessibility

Target WCAG 2.2 AA.

Required:
- visible focus ring;
- semantic headings;
- form labels;
- error association;
- keyboard-operable tables/actions;
- 44px touch target on mobile;
- chart text summaries;
- contrast-compliant badges;
- reduced motion support.

---

## 12. Suggested React Component Map

```text
AppShell
├── SidebarNavigation
├── TopBar
│   ├── GlobalSearch
│   ├── EnvironmentScope
│   ├── SystemHealth
│   └── UserMenu
├── PageHeader
├── FilterBar
├── KpiCard
├── IncidentTable
├── IncidentHeader
├── IncidentTimeline
├── EvidenceCard
├── EvidenceDrawer
├── AiInvestigationPanel
├── AiResponseCard
├── ToolExecutionStatus
├── MetricChart
├── LogViewer
├── TraceWaterfall
├── KafkaLagTable
├── DatabaseHealthPanel
├── KnowledgeResultCard
├── StatusBadge
├── SeverityBadge
├── EnvironmentBadge
├── SlaTimer
└── ConfirmActionDialog
```

---

## 13. Suggested Route Map

```text
/
/incidents
/incidents/:incidentId
/investigations
/investigations/:sessionId
/observability/metrics
/observability/logs
/observability/traces
/observability/kafka
/observability/database
/knowledge
/applications
/applications/:applicationId
/admin/access
/admin/policies
/admin/integrations
/admin/audit
```

---

## 14. Developer Handoff Checklist

Before a screen is complete:

- matches route and IA;
- responsive behavior implemented;
- loading/empty/error/partial states implemented;
- keyboard flow verified;
- focus visible;
- API errors mapped to useful UI messages;
- permission states implemented;
- environment always visible on operational screens;
- evidence sources deep-link correctly;
- AI facts and interpretation separated;
- no secret value rendered;
- destructive/high-impact actions confirmed;
- tests cover critical interactions.
