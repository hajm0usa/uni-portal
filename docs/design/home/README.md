# Home design prototype

This browser preview remains a design-only reference; the owner has approved its
native Compose implementation. The prototype itself does not touch authentication,
networking or Room. All course/event content and dates are
fictional, fixed examples. The owner-requested design target is all events in the
seven-day view, without a six-event cap. This is not yet a live-data guarantee:
the Android source still requests six events and needs confirmed pagination
semantics before it can safely implement a complete feed. Its existing limitation
labels remain unchanged until that implementation is possible.

## View

From the repository root:

```sh
python3 -m http.server 8765 --bind 127.0.0.1
```

Open <http://127.0.0.1:8765/docs/design/home/>. You can also open `index.html`
directly. Use the controls to change theme and content/loading/empty/stale/login/
section-error states. Buttons open explanatory previews, never real university
pages. There is no login form, network refresh, account access, or local storage.
Only Home is designed; other destinations intentionally remain placeholders.

On mobile, the review controls stay above the app preview; the bottom navigation
stays outside the content scroll. On desktop, explanatory notes sit beside the
phone-sized artboard. There are no external runtime resources or dependencies.

## Typography and color

- Page title: 28px/600. Featured title: 20px/500. Section headings: 16px/600.
- Event/course titles: 15px/500. Context: 12–14px/400. Metadata: 11–12px/400.
- Body text uses near-black/soft-white; secondary information is quieter but
  readable. Teal denotes actions/selection. Amber is limited to the sample
  deadline due tonight, not an invented production urgency policy.
- Uses real variable font weights, not browser-synthesized bold. Fixed teal is a
  design baseline; Android Material You adaptation is not simulated.
- Browser pixels are design references, not a substitute for Android sp/dp,
  accessibility font scaling, TalkBack, or native gesture testing.

## Assets

- University logo: reuses the existing app drawable without copying it.
- Vazirmatn variable WOFF2: official `rastikerdar/vazirmatn` release **v33.003**,
  `fonts/webfonts/Vazirmatn[wght].woff2`. SIL Open Font License 1.1; the existing
  license is at `app/src/main/assets/licenses/vazirmatn-OFL.txt`.
  Source: <https://github.com/rastikerdar/vazirmatn/tree/v33.003>.
  SHA-256: `4e3fa217d38fdafc1fea4414ceb58ca5e662cf0ab5fa735a8c8c20e8b42cad92`.

## Browser self-check

With the server running, open the same URL with `?test=1`. `home.test.js` checks
state transitions, saved-content retention, dialog interactions, real font
loading, text contrast in both themes, minimum touch targets, and horizontal
overflow at the current viewport. Results appear in the document and in
`document.documentElement.dataset.testStatus`. Run at 320px and desktop widths.

Native Android compilation and device verification are intentionally outside this
prototype. Review the visual design before implementation.

Verified locally: 90 browser assertions pass at actual emulated viewports of
320×900, 412×960, and 1280×1060. Light/dark and narrow-screen screenshots were
also visually reviewed. This is browser verification only, not Android testing.
