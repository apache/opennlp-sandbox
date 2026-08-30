# Research notes

Working notes behind the OpenNLP gRPC workbench: an industry audit of the
vocabulary the UI uses, a per-tab review of gating, empty states, and
cross-links, a test-coverage audit, and the roadmap of adjacent tracks.

Layout: `research/<theme>/` where each theme holds

- `README.md`: the analysis and decisions, written by the lead author.
- `findings/`: the raw write-ups the theme was built from.
- `reference/`: excerpts of external material consulted, each headed by its
  source URL and fetch date.
- `goals/`: the concrete, prioritised work items that came out of the theme.

Themes:

| Theme | Scope |
| --- | --- |
| `industry-terminology` | Glossary of every user-visible term, with verdicts and standard replacements |
| `analyze-tab` | Document analysis workbench |
| `workflows-tab` | Building workspaces and running corpus workflows |
| `corpus-search-tab` | Searching sealed indexes, the compound query builder |
| `workspace-search-tab` | Searching in-memory workspaces |
| `models-and-data-tab` | Catalog, installs, unlock tags, model zoo export |
| `trainer-tab` | Vocabulary learning and static model training |
| `lifecycle-tab` | Checkpoint, seal, aliases, collections, vocabulary drift |
| `test-coverage` | Coverage map and the drift bugs it let through |
| `roadmap` | Adjacent research tracks and how the themes relate to them |

Phased execution order with acceptance criteria: `GOALS.md`.

Nothing here is normative until it is lifted into `README.md`, `docs/rfc`, or
the code. Prose rules: no em dashes, cite code as `path:line`, separate fact
from recommendation, give each recommendation a priority (P1 to P3).
