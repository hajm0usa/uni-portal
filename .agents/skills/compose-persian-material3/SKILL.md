---
name: compose-persian-material3
description: Use when building Compose screens, theming (Material You), RTL/Persian text and digits, bottom navigation, state UIs, or Glance widgets in the university app.
---

# Compose, Persian RTL, Material 3

## Language and layout

- All user-visible strings in `res/values/strings.xml` (Persian). No hardcoded strings in Compose.
- `supportsRtl="true"`. Use start/end, never left/right. Icons that imply direction (back arrows, chevrons) must mirror; use `AutoMirrored` icons.
- Persian digits and number formatting: `NumberFormat.getInstance(Locale("fa"))`. Dates via the `persian-dates` skill.
- Bundle a Persian font (e.g. Vazirmatn) in `res/font`, set it in the theme typography. Do not rely on the system font.
- Never concatenate strings for sentences with numbers; use string resources with placeholders (word order differs).

## Theme (Material You)

- API 31+: `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
- API 26-30: `lightColorScheme` / `darkColorScheme` built from one base color (the owner chooses it in Slice 0).
- Support light and dark; follow the system setting.
- Glance widgets: `GlanceTheme.colors` (dynamic on 31+; use the fixed palette below). 

## Structure

- Shell: `Scaffold` + `NavigationBar` with Today, Courses, Calendar, Grades, Messages. Unfinished tabs show a "coming soon" card.
- Course detail is a nested screen (not a tab).
- One ViewModel per screen, exposing a single `UiState` via `StateFlow`; collect with `collectAsStateWithLifecycle`.
- Every data screen handles these states explicitly: loading (with cached content if any), content, empty, **stale** ("last updated 3 days ago" + refresh), **NeedsLogin** (clear banner and a sign-in button), **section error** (one card, not a crash).
- Pull-to-refresh on Today, Courses, Calendar, Grades.
- Unsupported sections: a card with an "open in browser" button that fires an external browser intent.

## Widget (Glance)

- Reads Room only. Shows the next few deadlines, "last updated" and a button that opens the app.
- Update after each successful sync via `GlanceAppWidget.updateAll`.
- Handle empty and `NeedsLogin`/stale states; never show a blank widget.

## Accessibility and polish

- Content descriptions in Persian, touch targets >= 48dp, readable contrast in both themes.
- Previews for each state (`@Preview`) using fake data, so the owner can check UI from CI screenshots if needed.

## Do not

- Do not use a WebView for content.
- Do not hardcode colors outside the theme.
- Do not show raw error messages from the server to the user; map them to short Persian messages and keep details in the log screen.
