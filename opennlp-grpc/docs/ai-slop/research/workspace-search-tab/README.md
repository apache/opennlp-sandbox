# Workspace search tab

Analysis of `findings/` for the tab labelled "Workspace search"
(`index.html:49`, section `session-search`, heading "On-the-fly workspace
index", controller `src/semantic-workbench.ts`).

## What a "dynamic workspace" is

One vector index (a "live index" from here on) the server built in its own memory from documents the user
analyzed, still accepting documents. There is no kind enum: "dynamic" is
`immutable == false` on one proto bool (`opennlp_search.proto:472-473`). A
workspace has three states the UI never shows: live (memory only), saved
(checkpointed, still writable), sealed (read-only). `persisted` is already on
the wire (`opennlp_search.proto:487-489`) and `search-adapter.ts:200-229` maps
every field except it.

## Verdicts

1. **The noun becomes "live index"; "dynamic" is deleted; state is shown.**
   Six names were costed (`findings/what-is-a-workspace.md` section 4). This
   researcher recommended keeping "workspace" to avoid churn in
   `WorkspaceCheckpointStore`, `search.persist.root` and the Lifecycle tab.
   The cross-tab audit found eight user-visible names for this one object and
   three of four researchers chose "live index", which is already the wire's
   own word (`SEARCH_PROVIDER_CAPABILITY_LIVE`); `../industry-terminology`
   records that decision and the Java churn is deferred to P3. "Collection"
   was ruled out because it is taken one level up. The tab becomes "Live
   index search". A chip reading "In memory", "Saved to disk" or "Read-only"
   next to each index in both pickers replaces the kind word, sourced from
   `immutable` and `persisted`.

2. **Onboarding pattern.** Of six patterns ranked
   (`findings/onboarding-patterns.md`), the winner is what the app already
   ships: a four-part first-run empty state (what this is, why it is empty,
   one primary action, one secondary link) plus a `details.help-callout`
   titled "What is a live index?". Tooltips are ruled out for vital
   information (Carbon, NN/g) and product tours are ruled out (NN/g negative,
   Atlassian deprecated its own). The explainer text in section 5 is adopted,
   including the paragraph that says a collection here is not what Qdrant or
   Solr mean by it.

3. **Heading and bridge text.** "On-the-fly workspace index" becomes "Search
   the documents you analyze"; the bridge sentence at `index.html:733` is
   replaced because it is false twice (saved-but-unsealed workspaces stay
   here, and Corpus search filters nothing).

4. **Bugs found.** "Clear workspace index" (`index.html:778`) deletes the
   index on the server, unconfirmed and untested, and its label does not say
   so. Every workspace is named "Workbench index"
   (`semantic-workbench.ts:331`), so the picker shows identical rows. The
   default storage cannot be saved (the same `flat_float` trap as Lifecycle;
   fixed by making the provider persistable). `LifecycleWorkbench` has no
   `onIndexChanged` (`main.ts:290-309`), so a sealed workspace stays in a
   stale picker as writable. `#workspace` is not cleared when the picker is
   rebuilt, so the tab can query an index the picker says is unselected.

5. **First run is a dead end.** `GET /api/v1/search-indexes` returns `{}`;
   four of five controls are disabled with no primary action, and the one
   button that creates a workspace is on the Analyze tab with no link.
   `service-info` carries no search capability flag, so the tab cannot tell
   "empty" from "dynamic indexing disabled" (HTTP 501). Decision: add a
   search capability block to `service-info` (dynamic indexing enabled,
   providers, persistence configured) so every search tab can brown out
   honestly.

6. **Links.** One outbound jump exists, to the tab a user least needs. Six
   are missing (Analyze to add documents, Lifecycle to save, Models & data for
   embeddings, Trainer for a distilled model).

7. **Tests.** 37 features mapped; the delete path, the Add button, the result
   list, every error path, `#index-count` and the provider lock have none.
   `test/index.test.ts:105` asserts the literal "On-the-fly workspace index"
   and will need the new heading.

## Open questions for the owner

- The HTML id `session-search` versus the label "Workspace search": align
  the id (the e2e spec references it) or leave it?
