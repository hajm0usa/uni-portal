# SSO login fixture

`login_form.html` is a sanitized structural excerpt of the owner's real SSO HTML, not a complete page. It preserves nested form tags, relevant control order, the button's `formaction`, and duplicate checkbox/hidden fields. Static layout, images, external scripts and links are omitted. Dynamic authorization state, client identifier and anti-forgery values are `XXXX`; no credentials/cookies from the capture are retained.

The owner also confirmed the browser's actual POST target and field order (see `docs/DISCOVERY.md`). DOM ownership must follow browser parsing, not XML parsing or form-tag regexes. This fixture proves structure only, not successful automatic login against the university. Changed forms and challenges are manual-only.
