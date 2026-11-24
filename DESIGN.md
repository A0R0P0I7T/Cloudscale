---
name: CloudScale
description: Restrained feature-delivery controls for organization-scoped teams.
colors:
  primary: "#1c625e"
  primary-hover: "#174f4c"
  signal-amber: "#bd8120"
  signal-amber-deep: "#76500f"
  page: "#edf1ef"
  surface: "#fbfcfa"
  surface-dim: "#e5ece9"
  ink: "#142c33"
  ink-soft: "#53676d"
  line: "#c9d5d1"
  line-strong: "#aebfba"
  rail: "#17373b"
  rail-muted: "#afc2bd"
  success: "#28634d"
  success-wash: "#deece4"
  danger: "#922f29"
  danger-wash: "#f3e1dd"
typography:
  headline:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "1.85rem"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.025em"
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "0.9rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0em"
  label:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "0.68rem"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "0.08em"
rounded:
  control: "0.45rem"
  panel: "0.5rem"
  large: "0.65rem"
  pill: "999px"
spacing:
  tight: "0.35rem"
  compact: "0.55rem"
  control: "0.8rem"
  section: "1rem"
  page: "2rem"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    rounded: "{rounded.control}"
    height: "2.55rem"
    padding: "0.55rem 0.9rem"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "#ffffff"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "2.55rem"
    padding: "0.55rem 0.9rem"
  text-field:
    backgroundColor: "#ffffff"
    textColor: "{colors.ink}"
    rounded: "0.38rem"
    height: "2.55rem"
    padding: "0.56rem 0.65rem"
---

# Design System: CloudScale

## Overview

**Creative North Star: "Release Ledger"**

CloudScale is a working surface for changing configuration deliberately. The visual language is restrained and confident: clear scope, legible state, and a short path from inspection to action. A deep teal navigation rail anchors the light workspace; amber marks active release progress, while green and red remain semantic.

The interface uses familiar controls and compact data layouts rather than decorative product chrome. The `CS` mark is live text, not a raster logo. No raster assets are shipped; Impeccable captures are review evidence only.

**Key Characteristics:**
- Configuration state is visible before mutation.
- The release timetable appears only where stages carry operational meaning.
- Monospaced text is reserved for keys, IDs, and measured values.
- Borders and tonal surfaces carry hierarchy; motion is brief state feedback.

## Colors

Harbor Teal is the action color, Signal Amber carries active release state, and the remaining palette separates neutral surfaces from semantic health states.

### Primary
- **Harbor Teal**: Primary actions, selected workspace emphasis, and focused action controls.
- **Harbor Teal Deep**: Hover state for primary actions.

### Secondary
- **Signal Amber**: Current rollout stage and caution state; use sparingly.
- **Signal Amber Deep**: Amber text on pale amber surfaces.

### Neutral
- **Cool Paper**: Page background.
- **Clean Surface**: Forms, panels, and tables.
- **Muted Surface**: Disabled controls and quiet secondary layers.
- **Deep Ink**: Main text and headings.
- **Soft Ink**: Supporting text and disabled labels.
- **Mist Line**: Dividers and control borders.
- **Deep Teal Rail**: Persistent workspace navigation.
- **Rail Mist**: Supporting labels on the navigation rail.

### Semantic
- **Forest Green** and its wash indicate healthy/enabled state.
- **Brick Red** and its wash indicate errors or rollback actions.

**The Signal-Only Rule.** Amber is reserved for active progression or caution; it is not a general decorative accent.

## Typography

**Display Font:** None; headings use the interface sans stack.  
**Body Font:** System sans stack.  
**Label/Mono Font:** System sans for labels; system monospace for identifiers and numeric data.

**Character:** Compact, familiar, and operational. Headings are medium-weight rather than display-driven; tabular numerals support comparison without turning monospace into a costume.

### Hierarchy
- **Headline:** Medium-weight, compact page title.
- **Title:** Short panel and detail headings.
- **Body:** Readable explanatory copy, kept to a comfortable measure.
- **Label:** Small uppercase context labels, used for field grouping rather than as decorative eyebrows.
- **Monospace:** Feature keys, IDs, revisions, percentages, and stage values only.

**The Data-Has-a-Job Rule.** Monospaced type is used only when values benefit from alignment or code-like distinction.

## Layout

Desktop uses a persistent left rail, a compact project/environment context bar, and a wide work area with tables and detail panels. The work area is capped to preserve readable scanning. At intermediate widths the rail becomes a top navigation strip and multi-column workspaces stack. At phone widths, selectors and forms become single-column while data tables retain horizontal scrolling.

The implementation’s layout transitions occur at 1050px, 820px, and 560px. Tight control groups use small gaps; page sections receive more separation. Prose is kept near a 65–75 character measure where the layout allows it.

## Elevation & Depth

The system is flat and border-led. Full borders, restrained surface shifts, and the dark navigation rail establish depth. A single low control shadow distinguishes the selected sign-in mode; panels do not rely on broad shadows.

**The Border-First Rule.** Use a full border or surface tint before adding elevation. Reserve shadows for a specific interaction state.

## Shapes

Controls and panels use gently curved, small-to-medium corners. Pills are limited to compact status tags and role labels. Inputs are full-width, border-led, and resize vertically only when they contain JSON or multiline data.

## Components

### Buttons
- **Primary:** Harbor Teal fill with white text; darkens on hover.
- **Secondary:** Surface fill with a stronger border.
- **Danger:** Pale red surface with semantic red text for explicit rollback.
- **Focus / press:** Visible amber focus outline and a brief, subtle press scale.
- **Disabled:** Remains legible with a muted surface; do not reduce the whole control’s opacity.

### Inputs / Fields
- **Style:** White fill, full border, consistent control height and compact padding.
- **Focus:** Visible amber outline with offset.
- **Error / disabled:** Errors use semantic red; disabled controls use a muted surface and readable soft-ink text.
- **Structured values:** JSON fields use the system monospace stack and preserve whitespace.

### Navigation
- **Desktop:** Persistent deep-teal rail with clear active surface and text state.
- **Mobile:** Horizontally scrollable top navigation; labels remain visible without decorative numbering.
- **Context:** Project and environment selectors stay together above the work area.

### Tables and State Tags
- **Tables:** Dense, aligned rows with clear headers, selected-row tint, and tabular numerals for revisions and percentages.
- **State tags:** Small pills distinguish enabled, disabled, warning, and version status.
- **Loading / empty:** Skeleton rows indicate loading; empty states explain the next real action.

### Release Stage Rail
- The stage rail uses ordered markers, target percentages, and durations because the sequence itself is operational information.
- Only the active stage receives amber emphasis; completed stages use semantic green.

## Do's and Don'ts

### Do:
- **Do** keep organization, project, and environment scope visible before an operation.
- **Do** use green, red, and amber only for their established semantic states.
- **Do** keep focus visible and honor reduced-motion preferences.
- **Do** show loading, empty, validation, permission, success, and error states honestly.

### Don't:
- **Don't** add percentage exposure to Evaluation until the value contract defines treatment/control behavior.
- **Don't** imply automatic rollback; health observations pause rollouts and rollback is manual.
- **Don't** add decorative section numbering, arbitrary dashboards, broad drop shadows, or fake/demo actions.
- **Don't** use monospace for ordinary labels or body copy.
