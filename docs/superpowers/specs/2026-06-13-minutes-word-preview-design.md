# Minutes Word Preview Design

## Context

The Meeting Detail page already has a Minutes tab that previews the signed
minutes PDF inline. It also has download actions for minutes artifacts, including
DOCX files. The requested change is to let users choose between viewing the
original signed PDF and previewing the edited Word file inside the page.

The selected implementation approach is frontend DOCX preview: the browser
downloads the private DOCX through the authenticated API and renders a read-only
HTML preview in the existing minutes viewing area.

## Goals

- Add an in-page view selector for minutes artifacts.
- Keep the current signed PDF preview for the original signed PDF.
- Add a read-only in-page preview for the edited Word file.
- Reuse the existing minutes download endpoint for DOCX bytes.
- Preserve existing download buttons for users who need the real file.
- Show clear loading, unavailable, and error states.

## Non-goals

- Do not build an in-browser Word editor.
- Do not require a public URL for Office or Google document viewers.
- Do not change the backend minutes download contract.
- Do not guarantee pixel-perfect Microsoft Word layout in the preview.

## User Experience

In the Minutes tab, place a segmented control above the preview area:

- `PDF goc co chu ky`: shows the existing signed PDF viewer.
- `Ban Word`: shows the edited Word preview.

The PDF option remains available when the signed PDF is available. The Word
option is enabled only when the edited Word artifact exists, using the existing
availability fields:

- `editedWordAvailable`
- `secretaryDocxAvailable`
- `secretaryDocxPath`

If the edited Word is not available, the selector keeps the Word option disabled
and the page continues to show the PDF preview by default.

## Data Flow

PDF preview:

1. `MeetingDetailPage` chooses the confirmed minutes version when minutes are
   host-confirmed or secretary-confirmed.
2. `MinutesViewer` fetches `format=pdf` through the authenticated API.
3. The component renders the PDF blob URL in the iframe.

Word preview:

1. User selects `Ban Word`.
2. A new Word preview component requests
   `/meetings/{meetingId}/minutes/download` with `version=secretary`,
   `format=docx`, and `responseType=blob`.
3. The component converts the DOCX payload to HTML in the browser.
4. The converted HTML is rendered read-only in the preview area.
5. Object URLs or transient resources are cleaned up on unmount/refetch.

## Components

`MeetingDetailPage` / `MinutesTab`

- Holds the selected preview mode: `pdf` or `word`.
- Computes whether the edited Word artifact is available.
- Resets to `pdf` if Word becomes unavailable after a minutes refresh.
- Renders the selector and the appropriate preview component.

`MinutesViewer`

- Remains responsible for PDF preview.
- No behavior change beyond being controlled by the selected preview mode.

`MinutesWordPreview`

- Fetches DOCX bytes with the shared authenticated Axios instance.
- Converts DOCX to HTML using a frontend DOCX-to-HTML library.
- Renders loading, unavailable, error, and successful preview states.
- Sanitizes or constrains rendered output enough for application-controlled
  minutes content.

## Error Handling

- Missing Word artifact: show an unavailable state instead of attempting fetch.
- 404 from DOCX download: show `Chua co ban Word da chinh sua.`
- Other fetch/conversion failures: show
  `Khong the tai ban Word. Vui long thu lai sau.`
- PDF errors continue to use the current PDF viewer error states.

## Testing

Add frontend tests for:

- The Minutes tab shows both preview choices when edited Word is available.
- Selecting the Word option renders the Word preview component.
- The Word option is disabled when edited Word is unavailable.
- The default preview remains the signed PDF.
- The Word preview requests `version=secretary` and `format=docx`.
- The Word preview shows loading/error/unavailable states.

## Risks

- Browser-side DOCX conversion may not match Microsoft Word exactly.
- Complex DOCX formatting, embedded objects, and advanced styles may be
  simplified by the converter.
- Adding a DOCX conversion dependency increases frontend bundle size.

These risks are acceptable for this phase because the preview is read-only and
the real DOCX download remains available.
