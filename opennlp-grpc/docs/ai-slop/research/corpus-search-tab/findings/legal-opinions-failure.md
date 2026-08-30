# "Legal opinions" search fails with "Failed to fetch"

Scope: the Corpus search tab (`#server-search`, `opennlp-grpc-webapp-default/index.html:546`).
Owner report, verbatim: "When attempting to search the 'Legal opinions' it's met with an error
'failed to fetch'. If it is because we need to create something, a link should be provided to the
user on what to do to stop that error."

Everything under FACT was observed in this worktree or against the live demo instances on
2026-08-28. Everything under OPINION is a recommendation.

## 1. Where "Legal opinions" comes from

FACT. The string `Legal opinions` exists in exactly one place in the repository, `README.md:673`,
as the `--display-name` of the worked TurboQuant bundle example:

```
--index-id legal-opinions \
--display-name "Legal opinions" \
```

and the matching serving configuration at `README.md:693-705` (`search.indexes=legal-opinions`,
`search.index.legal-opinions.provider=turbo_quant`, `max_top_k=50000`).

FACT. It appears nowhere in the front end. `grep -rni legal opennlp-grpc-webapp-default/index.html
opennlp-grpc-webapp-default/src opennlp-grpc-webapp-default/e2e` returns only unrelated
placeholders: `Legal dictionary` (index.html:908), `Legal vocabulary` (index.html:924),
`Legal static model` (index.html:946), `legal-current` (index.html:1023, index.html:1051),
`legal` (index.html:1069), `Legal corpus` (index.html:1073). There is no demo preset, no sample
index, and no hardcoded index id anywhere in the tab.

FACT. The index dropdown is populated only from the server. `src/main.ts:313` wires
`listIndexes: async () => readSearchIndexes(await getSearchIndexes())`, which calls
`GET /api/v1/search-indexes` (`src/api.ts`, `getSearchIndexes`). `#server-search-index` options are
built from the descriptors' `displayName` (`src/server-search-workbench.ts:151`,
`src/search-adapter.ts:202` `label: text(descriptor.displayName) || id`).

FACT (live state, 2026-08-28). All three demo instances report an empty index catalog:

```
GET http://127.0.0.1:7072/api/v1/search-indexes -> 200 {}
GET http://127.0.0.1:7172/api/v1/search-indexes -> 200 {}
GET http://127.0.0.1:7272/api/v1/search-indexes -> 200 {}
GET http://127.0.0.1:7172/api/v1/index-aliases  -> 200 {}
GET http://127.0.0.1:7172/api/v1/collections    -> 200 {}
GET http://127.0.0.1:7172/api/v1/search-providers -> 200 {"providers":[flat_float, terms, turbo_quant]}
```

So the label the owner saw came from an index that existed in their session and does not exist now:
either a startup bundle configured from the README recipe, or a dynamic workspace they built and
named. Both demo containers were restarted this morning (`opennlp-gpu-demo` at 02:40:28Z,
`opennlp-native-demo` at 03:24:31Z), and dynamic indexes that were never persisted do not survive a
restart (`opennlp_search.proto:453-456`: "Dynamic ids remain valid only for the lifetime of their
server process unless the index is persisted").

## 2. Reproduction of the request the front end sends

FACT. The compound-free path builds `{indexId, query:{rawText}, topK}`
(`src/search-adapter.ts:118-120`) and POSTs it to the relative path `/api/v1/search`
(`src/api.ts`, `searchIndex`). No source file in `opennlp-grpc-webapp-default` contains an absolute
URL, a hostname, or a port: `grep -n "7272\|127.0.0.1\|localhost" src/*.ts index.html
vite.config.ts` returns nothing. The front end is therefore always same-origin, and the
7272-versus-7172 cross-port hypothesis is ruled out. There is also no CORS involvement: the gateway
sends `Content-Security-Policy: default-src 'self'; connect-src 'self'; ...` and the page and the
API share an origin.

FACT. Issuing exactly that request for a missing index returns a clean, readable error:

```
POST http://127.0.0.1:7172/api/v1/search
{"indexId":"legal-opinions","query":{"rawText":"due process"},"topK":8}

HTTP/1.1 404 Not Found
Content-type: application/json; charset=utf-8
{"code":"NOT_FOUND","message":"Unknown dynamic search index 'legal-opinions'"}
```

FACT. That 404 does NOT surface as "Failed to fetch". `responseError` in `src/api.ts` reads
`body.message` and throws `new Error("Unknown dynamic search index 'legal-opinions'")`, and
`src/server-search-workbench.ts:279` renders it verbatim through
`errorMessage(error, "Search failed.")` (`src/ui-utils.ts:65-67` returns `error.message` whenever it
is nonblank). So a missing index shows "Unknown dynamic search index 'legal-opinions'" in
`#server-search-status`, not "Failed to fetch".

## 3. Root cause of "Failed to fetch": the gateway silently drops idle keep-alive connections

"Failed to fetch" is the browser's `TypeError` text for a network-level failure, so the request
never produced an HTTP response. The gateway does exactly that, deterministically, and this is
reproducible.

FACT. The gateway HTTP server is the JDK's built-in `com.sun.net.httpserver.HttpServer`
(`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java:32`,
created at line 94). Nothing in `opennlp-grpc-webapp/src` sets `Connection: close`, tunes
`sun.net.httpserver.idleInterval`, or otherwise manages connection reuse
(`grep -rn "keep-alive\|keepAlive\|idleInterval\|Connection" opennlp-grpc-webapp/src` finds
nothing). Responses are HTTP/1.1 with no `Connection` header, so keep-alive is implied and the
browser pools the connection. The JDK closes a pooled connection after its default idle interval
(30 seconds) without any close notification on the wire.

FACT (reproduced, 2026-08-28, against 127.0.0.1:7172 and 127.0.0.1:7272). A raw socket that sends
`GET /api/v1/search-indexes`, idles, and then sends the exact `POST /api/v1/search` body above on
the same connection:

| idle before the POST | result |
| --- | --- |
| 5 s | `HTTP/1.1 404 Not Found` with the JSON body |
| 31 s | `HTTP/1.1 404 Not Found` with the JSON body |
| 35 s | connection already closed, zero bytes returned |
| 45 s | connection already closed, zero bytes returned |
| 40 s (port 7272, native image) | connection already closed, zero bytes returned |

A browser in that state has already written the POST onto a socket the server closed. Chrome and
Firefox do not transparently retry a non-idempotent POST on a reused connection, so `fetch`
rejects with `TypeError: Failed to fetch`, and `src/server-search-workbench.ts:279` prints that
string as the search status.

FACT. This tab is the most exposed surface in the app for that race. Every other POST-driven tab
is reached through a click sequence that itself issues requests; on Corpus search the page load
issues the GETs that fill the dropdown, and the user then reads the index description, types a
query, and possibly assembles clauses before the first POST. Any pause over roughly 30 seconds
between page load (or the previous request) and pressing "Search index" lands on a dead pooled
connection.

FACT. The failure is transient and self-healing on retry: the browser opens a new connection for
the next attempt, so pressing Search again succeeds. That matches a report of a one-off "failed to
fetch" against an index that otherwise worked.

FACT. Nothing in the front end retries, and nothing re-reads the index catalog after a failure.
`ServerSearchWorkbench.initialize()` runs once at boot (`src/main.ts:432`) and again only when the
Workflows tab reports a new index (`src/main.ts:373-375`, `onIndexChanged`). Selecting the Corpus
search tab does not refresh the list, so a dropdown entry can name an index that the server no
longer has (after a restart, after `DeleteSearchIndex` on another tab, or after a container
restart), and the only signal the user gets is the raw error from the next search.

### Secondary contributor: the 404 text is wrong for a configured bundle

FACT. `OpenNlpSearchServiceImpl.findProvider` (line 938) resolves an alias, looks in the immutable
`registry`, and otherwise falls through to `dynamicRegistry.require(indexId)`, whose message is
`"Unknown dynamic search index '" + indexId + "'"`
(`DynamicSearchIndexRegistry.java:711`). The immutable registry has a better message,
`"Unknown search index 'X'; configured indexes: [...]"` (`SearchIndexRegistry.java:218`), but it is
unreachable on this path. A user who configured `search.indexes=legal-opinions` from the README and
mistyped the directory, or who is running a server build without the optional
`opennlp-grpc-search-turboquant` add-on on the classpath (README.md:112-115 lists it as optional),
gets told their bundle is an unknown *dynamic* index. Nothing in that sentence points at the
configuration key that is actually wrong.

## 4. What the front end already knows in advance

FACT. Before any search runs, the tab holds the full descriptor list in `#indexes`
(`src/server-search-workbench.ts:102`), each carrying `id`, `label`, `immutable`, `maxTopK`,
`maxQueryBytes`, `supportsAllHits`, `size`, `dimension`, provider and corpus identity
(`src/search-adapter.ts:33-53`). It therefore already knows, without a round trip:

- whether the selected id is still in the catalog (it can re-list on tab activation);
- whether the catalog is empty at all (handled today, but with misleading copy: see
  `findings/gating-and-links.md`).

FACT. It also *could* know two things it currently discards. `SearchIndexDescriptor.components`
(`opennlp_search.proto:485-487`) declares each modality of the index and
`SearchComponentKind` (`opennlp_search.proto:507-515`) distinguishes
`SEARCH_COMPONENT_KIND_VECTOR` from `SEARCH_COMPONENT_KIND_KEYWORD`. `readSearchIndexes`
(`src/search-adapter.ts:200-229`) does not read `components` or `persisted` at all. That is the
data that would let the builder disable term and phrase clauses on an index that has no keyword
component instead of letting the server answer 501.

### OPINION (P1): distinguish network failure from server failure

Current behaviour: any thrown error becomes the status line, so "Failed to fetch" is presented as
though it were the server's answer.

Proposed: in `ServerSearchWorkbench.search`'s catch (`src/server-search-workbench.ts:275-280`),
branch on `error instanceof TypeError` (that is exactly the network-level case) and render:

> "The server did not answer. The connection may have been dropped or the service restarted.
> Search again, or reload if it keeps failing."

with a "Search again" affordance, and re-run `initialize()` so a stale dropdown is refreshed. A
single automatic retry of the POST would remove the visible symptom entirely, because the retry
opens a fresh connection.

### OPINION (P1): fix the dropped connection at the gateway

The browser-side workaround treats the symptom. The gateway can remove the race outright, in
descending order of preference:

1. Set a much longer idle interval at startup (`System.setProperty("sun.net.httpserver.idleInterval", ...)`)
   so a browser's pooled connection is not reaped mid-session, or
2. send `Connection: close` on API responses so the browser never pools an API connection (costs a
   handshake per request, acceptable for a local workbench), or
3. document the JVM property in `docker/README.md` and the demo images.

Option 1 or 2 belongs in `OpenNlpGrpcWebServer`, next to the security headers it already adds.

### OPINION (P1): make the missing-index error actionable, with a jump link

Current behaviour for a genuinely missing index: the status reads
`Unknown dynamic search index 'legal-opinions'` and nothing else changes.

Proposed status when the search 404s and the id is absent from a freshly re-listed catalog:

> "No index named 'Legal opinions' exists on this server any more. Dynamic workspaces are lost when
> the server restarts unless they were saved or sealed. Build one on **Workflows**, or save and
> seal it on **Lifecycle** so it survives a restart."

with **Workflows** and **Lifecycle** rendered as the existing `data-workbench-jump` link buttons
(the pattern is already used at index.html:555 and index.html:592, and
`WorkbenchNavigation` binds every `[data-workbench-jump]` element,
`src/workbench-navigation.ts:38-41`).

### OPINION (P2): fix the server-side message for configured bundles

`findProvider` should report the immutable registry's message when the id is not a dynamic index,
so an operator sees `Unknown search index 'legal-opinions'; configured indexes: [...]`. Even
better, when `search.indexes` names an index whose provider id is not registered, say so:
"index 'legal-opinions' requests provider 'turbo_quant', which is not on the classpath; add the
opennlp-grpc-search-turboquant add-on".

## 5. Answer to the owner's question

The error is not caused by "needing to create something". A missing index produces a readable 404
message, not "Failed to fetch". "Failed to fetch" is the browser reporting that the POST never
reached a live connection, and the reproducible cause is the gateway closing pooled keep-alive
connections after about 30 seconds of idleness with no close signal. The two fixes are independent
and both worth doing: stop dropping the connection (gateway), and when a search really does fail
because the index is gone, say so with a jump link to Workflows or Lifecycle instead of echoing a
raw exception message.

## Questions for the lead

1. Do we want the front end to retry a failed search POST once automatically, or only offer a
   visible "Search again" button? A silent retry hides a real outage; a button costs one extra
   click on a failure the user did not cause.
2. Should the Corpus search tab re-list indexes every time it is shown? That makes stale entries
   self-correcting but adds a request per tab switch.
