# StayDesk Documentation

Staff-facing how-to guides, SOPs, and quick-reference cards for the StayDesk motel management system. Source is Markdown; output is PDF.

## Folder layout

```
docs/
  training/          — how-to guides for front desk staff and new hire onboarding
  sops/              — standard operating procedures and daily checklists
  quick-reference/   — short-form cheat sheets meant for printing and posting
  style/             — shared CSS stylesheet applied to all PDFs
```

## Building PDFs

Requires [md-to-pdf](https://github.com/simonhaenisch/md-to-pdf):

```bash
npm install -g md-to-pdf
```

**Build all docs (from repo root):**
```bash
node docs/build-pdfs.mjs
```

The build script inlines screenshots as base64 before rendering so images appear correctly in the output. PDFs are written alongside their source `.md` file and are gitignored — only the Markdown source and screenshots are committed.

**Build a single file manually:**
```bash
md-to-pdf --stylesheet docs/style/staydesk.css docs/training/04-check-in.md
```
Note: single-file builds may not render images due to Chromium `file://` restrictions. Use the build script for reliable output.

## Adding screenshots

1. Take a screenshot of the relevant UI.
2. Save it as a PNG to the `assets/` folder inside the same directory as the doc (e.g. `docs/training/assets/check-in-step1.png`).
3. Reference it in Markdown:
   ```markdown
   ![Check-in form](assets/check-in-step1.png)
   *The check-in form after scanning the confirmation.*
   ```
   The italicised line after the image renders as a caption.

## Updating docs

All documents include a `last_updated` field in their front matter. Update it whenever content changes. Rebuild the PDF and the updated file is ready to share or print.
