# Corpus search tab

Analysis of `findings/` for the tab labelled "Corpus search" (`index.html:47`,
section `server-search`, heading "Explore an immutable index", controller
`src/server-search-workbench.ts`, builder `src/query-builder.ts`).

## The "Legal opinions" failure

"Legal opinions" exists only in `README.md:673` as the display name in the
bundle example; no demo has such an index, and all three return `{}` from
`/api/v1/search-indexes`. A genuinely missing index does not produce
"Failed to fetch": it produces a clean 404
`Unknown dynamic search index 'legal-opinions'` (verified live).

"Failed to fetch" is the browser's `TypeError` for a network-level failure.
Two causes fit what the owner saw:

1. **Confirmed at the socket level:** the gateway is the JDK's
   `com.sun.net.httpserver` (`OpenNlpGrpcWebServer.java:32,94`) with the
   default `sun.net.httpserver.idleInterval` of 30 s and no
   `Connection: close`. A pooled keep-alive connection idle for 33 s gets zero
   bytes back on the next POST (reproduced on 7172). A browser that reuses a
   socket the server closed in the same instant surfaces exactly this error,
   and this tab is the most exposed because the first search usually follows a
   long human pause.
2. The native demo on 7272 was unhealthy for part of yesterday (the catalog id
   defect fixed in `7938d722`), during which every fetch failed the same way.

Decision: fix both sides regardless. Gateway: set the idle interval well above
any human pause (or send `Connection: close`), in the server constructor so it
applies to the native image too. Front end: branch on `TypeError` in
`server-search-workbench.ts:279`, retry a search POST once (it is a read), and
otherwise show "The server did not answer. Check the service status light and
try again." instead of the raw message. And a missing index gets its own text:
"No index named X exists. Build one on Build index or ask the operator for a
bundle." with a jump.

A second defect hides behind the first: `findProvider`
(`OpenNlpSearchServiceImpl.java:938-942`) falls through to the live registry, so
a configured read-only bundle whose provider add-on jar is missing is reported
as an unknown *live* index. That message must name the missing add-on.

## Verdicts

1. **Heading.** "NLP search index creation" is rejected: the tab only reads.
   "Immutable" is also wrong: `ListSearchIndexes` returns bundles and live
   indexes and the tab filters nothing (`server-search-workbench.ts:141`).
   Decision: "Search an existing index", with the bridge sentence corrected to
   say it searches every index the server holds. Six alternatives are costed in
   `findings/naming-alternatives.md` section 3; the runner-up "Search a corpus
   and inspect the evidence" names the inspector, which is this tab's real
   differentiator, and can be the kicker.

2. **Advanced search.** "Compound query builder" becomes "Advanced search: mix
   keyword and semantic clauses". The bigger defect: the builder does not work
   on read-only TurboQuant bundles at all (`TurboQuantProvider` inherits
   `queryCandidates() -> null`, so every compound query returns 501), while
   the help text promises it, and the FE discards `descriptor.components`
   which would let it gate term and phrase clauses. Decision: read the
   components, disable the clauses the index cannot serve, and say why; and
   track compound support in the TurboQuant provider as a feature.

3. **Terminology.** Mostly standard (hit, term, phrase, slop, chunk, cosine,
   RRF). Fixes: "Results" as the top-k label contradicts "0 hits" (becomes
   "Max hits"); "Join" means a cross-index join everywhere else (becomes
   "Combine clauses" with "Must match every clause" style options); the tab
   is hybrid search and never says so (say it once); "embedding route" and
   "artifact hash" in the inspector are invented (become "Embedding model
   used" and "Model checksum"). Filter and boost clauses exist in the proto
   and are not exposed; that is a P3 feature, not a naming issue.

4. **Links.** Missing jumps: empty index list to Build index; search failure
   because the index is gone to Build index and Lifecycle; pinned embedding
   model not loaded to Models & data.

5. **Tests.** 25 untested features including every empty state, the failure
   path, the whole compound submit path, all heatmap DOM and all 19 inspector
   facts. `e2e/corpus-search.spec.ts` skips itself when no index exists, which
   is the default demo state, so it has likely never completed. The docker
   smoke in `../test-coverage` must ship a small bundle so it can.

## Open questions for the owner

- Retry a failed search POST once silently (recommended), or only offer a
  "Search again" button?
- Re-list indexes every time the tab is shown? (Recommended yes; one cheap GET.)
