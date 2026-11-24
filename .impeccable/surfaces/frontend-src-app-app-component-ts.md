---
version: 1
slug: "frontend-src-app-app-component-ts"
primary_target: "frontend/src/app/app.component.ts"
related_targets: []
---

# CloudScale Management Workspace

Scope: the single-page Angular management client served by `frontend/`, authenticated through Auth Service and all API traffic routed same-origin through the Gateway.

Mode: Operate.

Audience and job: a developer or product operator signs in, selects an organization, project, and environment, then edits or publishes feature configuration, evaluates a user context, manages rollout state, and reviews audit events.

Confirmed hierarchy: Feature Flags is the initial workspace (user-selected); Evaluation, Rollouts, and Audit remain one click away. Project/environment selection is persistent shell context.

Requirements: real API requests only; loading, empty, validation, and error states; no false success. Feature drafts and publishing use existing Configuration APIs. Evaluation shows returned result and reason. Rollout assignment is diagnostic only because its percentage is not connected to Evaluation's single-value contract. Health automation only pauses after the implemented sample floor and threshold; rollback is manual.

Evidence and limits: no user-provided brand assets, real tenant data, or production measurements. The default screen is an honest empty state. Any local demonstration values are entered by the operator, not seeded or presented as customer data.

## Direction contract

THESIS: Feature delivery is a sequence of controlled release steps, so the operator should see current configuration state before acting.

OWN-WORLD: A release-gate timetable contributes tabular numerals, a restrained signal palette (ink, paper, amber, green, and red only for semantic state), dense aligned rows, and one active-stage rail. It does not dictate navigation, controls, or page layout.

STORY: The operator sees the selected organization/project/environment, scans the feature list and published/draft state, opens one feature, then publishes or evaluates it; Rollouts and Audit are adjacent workspaces.

FIRST VIEWPORT: Standard left navigation; a compact top context selector; a clear “Feature flags” heading and create action; a semantic table of real API rows or a useful empty state; the selected environment is visible before any mutation.

FORM: Release-gate timetable, grounded candidate 3 assigned for this Operate surface; the assigned zoo-map direction is not used because geography does not truthfully map to features or environments. Seed reference: `de8ae6c1d8c8df1c`.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
