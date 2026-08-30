# Lifecycle tab

Analysis of `findings/` for the tab labelled "Lifecycle" (`index.html:55`,
section `lifecycle-workbench`, controller `src/lifecycle-workbench.ts`).

## What the tab does

Checkpoint or seal an in-memory index, point an alias at an index, rebuild an
index with a different embedding model, and group indexes into collections
whose vocabulary coverage the server streams live. The state diagram and the
mapping to Lucene, Elasticsearch, Solr, Qdrant and Git vocabulary are in
`findings/state-machine-and-vocabulary.md` sections 1 and 2.

## Verdicts

1. **The "seal" error the owner hit is a product defect, not an empty state.**
   With no workspaces the buttons are disabled, so the empty case cannot
   error. The real path: both creation tabs default to `flat_float`
   (`index.html:748,450`, `DynamicSearchIndexRegistry.java:845`) and only
   TurboQuant declares `SEARCH_PROVIDER_CAPABILITY_PERSISTENT`, so seal and
   checkpoint on the default index always fail with HTTP 412
   "Search provider instance 'flat_float' is not persistent". Nothing on any
   tab warns about this. Decision: make `flat_float` persistable. A flat float
   array is the easiest thing to write to disk, and fixing the provider removes
   the trap everywhere instead of adding warnings on three tabs. Until it
   lands, gate the two buttons on the `persistent` capability and say why.
   The S3 add-on is unrelated: it stores vocabulary artifacts, checkpoints are
   local filesystem only, and the tab must not imply otherwise.

2. **Persist and seal are one write.** Seal is the same call with a flag
   (`DynamicSearchIndexRegistry.java:519-546`), and a sealed workspace can
   still be deleted; only startup bundles are truly immutable. So the UI must
   stop presenting checkpoint and seal as rungs of a ladder and stop saying
   "permanently read-only". Also, a successful seal removes the workspace from
   the tab (`lifecycle-workbench.ts:129` filters immutable), so the "Sealed"
   fact row can only ever say "no". Sealed workspaces stay listed, flagged
   read-only, with a jump to Workspace search where they remain searchable.
   `docs/rfc/opennlp-search-query-model.md:146` ("turns one into an immutable
   bundle") is corrected to match.

3. **Names.** "Save checkpoint" becomes **"Save to disk"**: this researcher
   argued to keep "checkpoint", but the cross-tab audit showed it collides
   with model weights (Hugging Face, MLflow) one panel away from "Serving
   model artifact", and two of three researchers chose the plain verb. The
   on-disk copy is not called a "snapshot" (Qdrant, OpenSearch and Weaviate
   snapshots are restorable point-in-time copies, which the server does not
   offer). "Seal as read-only" becomes **"Make read-only"**, Elastic's own
   phrase for `index.blocks.read_only`; "seal" exists only in Milvus and
   "freeze" imports a deprecated Elastic concept. "Dynamic workspace" becomes
   "Live index" per `../industry-terminology`. "Provider instances" becomes
   "Vector storage available on this server". Tooltips for every control are
   drafted in `findings/state-machine-and-vocabulary.md` section 4 and
   adopted with the nouns swapped.

4. **"Collection" is a false friend but stays.** In Qdrant, Solr and Weaviate
   a collection is the searchable thing; here it is a named group of
   live indexes watched together and cannot be searched. It is on the wire in
   five RPCs, so the word stays and gets an on-screen definition under the
   heading plus a flyout (text in section 3.3). Second choice if the owner
   wants a rename: "Drift group" in the UI, `collection_id` on the wire.

5. **"Vocabulary drift" becomes "Vocabulary coverage".** The Trainer
   researcher argued to keep "drift" (a real term, arXiv:2305.17127); the
   Lifecycle researcher showed the panel computes `1 - OOV rate` with no time
   axis, and the meter's own `aria-label` already says "Vocabulary coverage".
   Both are right: the metric is coverage, and falling coverage over time is
   vocabulary drift. Decision: heading "Vocabulary coverage", the flyout
   (adapted from `../trainer-tab/findings/terminology.md` section 10) defines
   vocabulary, says the number is an out-of-vocabulary rate, and names the
   threshold alert as the drift signal; the threshold label becomes
   "Alert after this many out-of-vocabulary terms". With no vocabulary
   artifact the meter shows "not measured" instead of 0%.

6. **Zero cross-tab links, nine needed** (`findings/links-and-tests.md`
   table): empty state to Workflows and Workspace search, rebuild to Trainer,
   vocabulary artifact to Trainer (no list endpoint exists, so a picker needs
   a new `ListVocabularies` RPC), dictionary artifact becomes a select fed by
   `/api/v1/dictionaries`, post-seal and post-rebuild to Workspace search,
   coverage 0% to Trainer.

7. **Tests.** Service coverage is good, including both failure preconditions.
   `lifecycle-workbench.ts` (568 lines) is the only workbench module with no
   unit test, eleven `api.ts` lifecycle functions are untested, and no e2e
   spec touches the tab.

## Open questions for the owner

- Make `flat_float` persistable (recommended), or change the default storage
  to TurboQuant where the add-on is present?
- Is "collection" permanent API vocabulary? (Assumed yes.)
