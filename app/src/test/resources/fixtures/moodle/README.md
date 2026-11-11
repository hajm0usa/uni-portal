# Moodle fixture provenance

`courses_ok.json` is sanitized from the owner's saved dashboard response for `core_course_get_enrolled_courses_by_timeline_classification`. It is not a fabricated server response.

- Preserved: the full one-item response array, `error: false`, every course key and value type, all 21 course objects, empty strings, course configuration flags and `nextoffset: 21`.
- Replaced: course IDs/names/codes/category, dates, links and images with deterministic sample values; progress and favourite state with neutral values. Links use the reserved `example.invalid` host. The replacement SVG is a newly generated empty image, not decoded original content.
- The original is only in Git-ignored `fixtures/raw/courses-response.json`; do not commit or publish it.
- JSON validity, recursive structure/type equivalence to the original, and sanitization are checked locally. `FixturePrivacyTest` scans JSON/HTML fixtures for obvious emails, long identifier-like numbers (except course epoch fields), session cookies and unredacted credential fields/URL parameters, including OAuth callback credentials and percent-encoded query delimiters. This is a guard, not a substitute for sanitizing names, images and free text. `MoodleParsersTest` now consumes the fixture and checks the mapped shared course models and preserved offset.

This sample alone does not establish pagination termination or expired-session/error response shapes. Real empty, server-error and HTML-login responses still need collection; do not infer their shapes from this success case.

`moodle_config_ok.html` contains the real `M.cfg` assignment from the owner's Git-ignored `fixtures/raw/moodle-config.html`. Only the enclosing script tag and provenance comment were added; this is a fragment, not a complete captured dashboard page. Assignment spelling, whitespace, JSON keys, nesting, value types, booleans, nulls and empty containers/strings are preserved. All nonempty string values (including sesskey, URLs and configured timeout/revision strings) are replaced with `XXXX`; all numeric values are replaced with zero. No actual credential, user/context ID or configured timeout value is retained. The original JSON-compatible object and sanitized fragment are checked locally for structure/type equivalence and complete value redaction. `MoodleParsersTest` verifies extraction of the placeholder sesskey from this fragment; live dashboard fetching and WebView/OkHttp handoff remain unverified.

## Explicitly synthetic cases

Every `*_synthetic.json` / `*_synthetic.html` file is an invented test case, not an observed university response:

- `courses_empty_synthetic.json`: empty array handling; its offset does not establish server pagination semantics.
- `courses_expired_session_synthetic.json`: boolean error plus `exception.errorcode = servicerequireslogin`.
- `courses_invalid_sesskey_synthetic.json`: boolean error plus `exception.errorcode = invalidsesskey`.
- `courses_error_object_synthetic.json`: object-valued error handling.
- `courses_login_page_synthetic.html`: HTML-instead-of-JSON detection, not an actual AJAX response.

Additional malformed/missing/null/unknown-field cases are constructed explicitly in the tests. Passing these tests proves local parser behavior, not live session-expiry detection, retry behavior or end-to-end sign-in.
