# Moodle fixture provenance

`courses_ok.json` is sanitized from the owner's saved dashboard response for `core_course_get_enrolled_courses_by_timeline_classification`. It is not a fabricated server response.

- Preserved: the full one-item response array, `error: false`, every course key and value type, all 21 course objects, empty strings, course configuration flags and `nextoffset: 21`.
- Replaced: course IDs/names/codes/category, dates, links and images with deterministic sample values; progress and favourite state with neutral values. Links use the reserved `example.invalid` host. The replacement SVG is a newly generated empty image, not decoded original content.
- The original is only in Git-ignored `fixtures/raw/courses-response.json`; do not commit or publish it.
- JSON validity, recursive structure/type equivalence to the original, and sanitization are checked locally. `FixturePrivacyTest` scans JSON/HTML fixtures for obvious emails, long identifier-like numbers (except course epoch fields), session cookies and unredacted credential fields/URL parameters. This is a guard, not a substitute for sanitizing names, images and free text. The fixture is not yet consumed by a Kotlin parser test.

This sample alone does not establish pagination termination or expired-session/error response shapes. Empty, server-error and HTML-login fixtures still need collection (or explicit synthetic labels); do not infer their shapes from this success case.

`moodle_config_ok.html` contains the real `M.cfg` assignment from the owner's Git-ignored `fixtures/raw/moodle-config.html`. Only the enclosing script tag and provenance comment were added; this is a fragment, not a complete captured dashboard page. Assignment spelling, whitespace, JSON keys, nesting, value types, booleans, nulls and empty containers/strings are preserved. All nonempty string values (including sesskey, URLs and configured timeout/revision strings) are replaced with `XXXX`; all numeric values are replaced with zero. No actual credential, user/context ID or configured timeout value is retained. The original JSON-compatible object and sanitized fragment are checked locally for structure/type equivalence and complete value redaction. App extraction is not implemented or verified yet.
