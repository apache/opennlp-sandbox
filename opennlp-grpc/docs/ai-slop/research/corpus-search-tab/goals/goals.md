# Goals: Corpus search tab

## P1

- [x] Gateway: raise `sun.net.httpserver.idleInterval` (or send `Connection: close`)
      in `OpenNlpGrpcWebServer`; test that a POST after a 35 s idle succeeds.
- [x] FE: branch on `TypeError` at `server-search-workbench.ts:279`, retry a search
      once, then show a plain "server did not answer" message.
- [x] Missing-index message with a jump to Build index; missing provider add-on
      named in the service error instead of "unknown dynamic index"
      (`OpenNlpSearchServiceImpl.java:938-942`).
- [x] Heading "Search an existing index"; corrected bridge sentence; kicker
      names the evidence inspector.
- [x] "Advanced search: mix keyword and semantic clauses"; read
      `descriptor.components` and disable clauses the index cannot serve, with the reason.
- [x] Empty index list links to Build index; failure states link to Lifecycle and
      Models & data.
- [ ] Tests: empty states, failure path, compound submit path, keep-alive reuse
      in the gateway; ship a small bundle in the docker smoke so the e2e spec runs.

## P2

- [x] "Results" to "Max hits"; "Join" to "Combine clauses" with plain option labels;
      say "hybrid search" once; inspector labels "Embedding model used" and
      "Model checksum".
- [ ] Re-list indexes when the tab is shown.
- [ ] Compound query support in the TurboQuant provider.
- [ ] Tests for the heatmap DOM and the inspector facts.

## P3

- [ ] Expose filter and boost clauses (`CelFilterClause`, `BoostClause`).
