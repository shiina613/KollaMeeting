# Minutes responsive viewer and structured editor design

## Context

Meeting detail page currently embeds minutes PDF in a fixed-width application layout. On smaller screens, sidebar/header offsets stay fixed, action rows become too wide, and native PDF viewer leaves excessive empty space on the right. Current minutes editing uses `contentHtml`, but desired workflow is structured minutes editing that produces an editable Word document, not HTML editing.

## Goals

- Make protected app layout responsive across desktop, tablet, and mobile.
- Reduce excess right-side space in minutes PDF view by adding user-controlled zoom.
- Simplify minutes file workflow around two concepts: raw STT reference and edited Word version.
- Replace HTML-based minutes editing with structured web editing for allowed content fields.

## Non-goals

- No browser-based Microsoft Word editor.
- No direct in-place mutation of existing DOCX XML.
- No PDF generation or signing for the edited version in this phase.
- No version history for edited Word files.

## Responsive Layout

Desktop (`lg` and wider): keep existing fixed sidebar and offset header/main layout.

Tablet/mobile (`< lg`): hide fixed sidebar, make header full width, and add a menu button. The menu button opens the existing sidebar as a left drawer. Selecting a navigation item or clicking backdrop closes the drawer. Main content removes fixed left margin and uses smaller responsive padding.

## Minutes Tab Workflow

Before an edited Word version exists:

- Show raw signed PDF generated from STT output.
- Show PDF zoom control with `Fit width`, `100%`, and `125%`.
- Provide entry point for structured editing.

After an edited Word version exists:

- Hide raw PDF preview.
- Show edited Word version as primary artifact.
- Allow editing again; saving overwrites current edited Word version.

## File Semantics

Raw version:

- Raw signed PDF: generated from STT output and used as reference.
- Raw Word: generated from same source/template as raw PDF and used as initial edit source.

Edited version:

- Separate Word file generated from structured edited data.
- Can be regenerated and overwritten after subsequent edits.
- No edited PDF is generated in this phase.

## Structured Editor

Editor is form-based, not raw HTML and not direct DOCX editing.

Editable fields:

- Speech/content entries from STT minutes.
- Conclusion/summary text.

Read-only fields:

- Meeting title/code.
- Start/end times.
- Host.
- Secretary.
- System-derived meeting metadata.

Data flow:

1. Backend creates raw minutes from STT and stores structured minutes data.
2. Frontend loads structured minutes data into form sections.
3. User edits only allowed fields.
4. Backend validates allowed fields and renders edited Word from template.
5. Edited Word replaces previous edited Word if one exists.

## Components

- `AppLayout`: responsive shell state, drawer open/close behavior.
- `Header`: mobile menu trigger.
- `Sidebar`: reusable in fixed desktop mode and drawer mobile mode.
- `MinutesViewer`: PDF zoom control and responsive iframe height.
- `MinutesDownloadButtons`: simplified actions matching raw PDF/raw or edited Word workflow.
- `MinutesEditor`: replaced or refactored into structured form editor.
- Backend minutes service/controller: expose structured edit data, accept structured edits, render edited DOCX.

## Error Handling

- If raw PDF is unavailable, show current unavailable/error state.
- If edited Word generation fails, keep previous edited Word and show error.
- If user attempts to edit read-only metadata, backend ignores/rejects those fields.
- If drawer is open and route changes, close drawer.

## Testing

Frontend tests:

- Responsive layout classes/behavior for desktop and mobile header/sidebar.
- Drawer opens/closes from menu/backdrop/navigation.
- Minutes viewer changes PDF URL/hash or viewer state for zoom choices.
- Minutes tab shows raw PDF before edited Word exists and hides it after edited Word exists.
- Structured editor renders editable and read-only sections correctly.

Backend tests:

- Structured minutes update only changes allowed fields.
- Edited Word generation overwrites current edited Word path/state.
- Raw signed PDF remains unchanged after edited Word save.
- Download endpoints resolve expected raw/edited artifacts.

## Open Decisions

All major product decisions are resolved for this phase:

- Sidebar mobile behavior: drawer.
- PDF zoom: user-controlled zoom selector.
- Raw reference: signed PDF from STT output.
- Editing mode: structured form editor.
- Editable scope: speech/content and conclusion only.
- Edited output: Word only.
- Edited version policy: overwrite existing edited Word.
