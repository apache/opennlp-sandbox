# The "Vocabulary drift" panel: what it measures, and what to call it

Owner's words: *"'Vocabulary drift' can even have a flyout for what 'vocabulary' means in the
help."* and *"I think we need better help annotations on the lifecycle and/or vocabulary change."*

UI: `opennlp-grpc-webapp-default/index.html:1100-1105`, rendered by
`LifecycleWorkbench.renderCollection` (`opennlp-grpc-webapp-default/src/lifecycle-workbench.ts:478-517`).

---

## 1. What it computes, exactly

FACT. All of it comes from `SearchCollectionRegistry.describe(...)`
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/SearchCollectionRegistry.java:646-692`)
with the counting helper `countUnits(...)` at lines 723-761. Nothing is stored; every read
recomputes from live member-index content.

The algorithm:

1. Load the term set of the collection's configured `vocabulary_artifact_id`. If none is
   configured, this set is **empty** (`vocabularyOf`, `SearchCollectionRegistry.java:702-712`).
2. For every member index, for every retained chunk, run the chunk's `indexedText` through
   `QueryTermAnalyzer.analyze(...)`, the same analysis chain the keyword search components use.
   Multiword vocabulary terms are matched greedily longest-first and counted as one unit; every
   other word is counted individually. Result: a `Map<term, occurrences>`.
3. Aggregate (`SearchCollectionRegistry.java:650-668`):

```java
for (Map.Entry<String, Long> entry : counts.entrySet()) {
  occurrences += entry.getValue();
  if (!vocabulary.contains(entry.getKey())) {
    newTerms++;
    newOccurrences += entry.getValue();
  }
}
```

So, precisely:

| Field | UI label (`lifecycle-workbench.ts`) | Definition |
| --- | --- | --- |
| `distinctTerms` | "Distinct terms" (:488) | `counts.size()`, distinct analyzed term units across all member indexes |
| `termOccurrences` | "Occurrences" (:489) | total occurrences of all term units |
| `newTerms` | "New terms" (:490) | distinct term units **absent from the vocabulary artifact** |
| `newTermOccurrences` | "New occurrences" (:491) | occurrences of those absent term units |
| `vocabularyCoverage` | the meter and its label (:493-497) | `(occurrences - newOccurrences) / occurrences`, or `0` when `occurrences == 0` |

The threshold field, labelled "Report vocabulary drift after this many new terms"
(`index.html:1091`), fires the `COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED` watch event once
`newTerms` crosses it upward (`SearchCollectionRegistry.notifyIndexed`, lines 477-514).

---

## 2. This is not drift

OPINION, on FACT. Three things are wrong with the name.

**It has no time axis.** Drift, in every definition, is change over time. Wikipedia's opening line:
"Concept drift or drift is an evolution of data that invalidates the data model. It happens when the
statistical properties of the target variable ... change over time in unforeseen ways"
(`../reference/drift-terminology.md`). The computation above compares one static set against another
static set. There is no earlier measurement, no window, no baseline over time. Re-reading the same
unchanged collection returns the same number forever. Nothing evolves.

**It has no target variable.** Concept drift is defined relative to what a model predicts. This panel
never touches predictions, labels, or model outputs. It counts string membership.

**What it actually is has a standard name.** The fraction of tokens absent from a fixed vocabulary is
the **out-of-vocabulary (OOV) rate**, a coverage statistic from language modelling and speech
recognition. `newTermOccurrences / termOccurrences` is the token-level OOV rate;
`newTerms / distinctTerms` is the type-level OOV rate; `vocabularyCoverage` is exactly `1 - OOV
rate` at the token level. The panel is a **vocabulary coverage** meter.

FACT, and this is the strongest evidence: the UI's own second heading already gets it right. The
list underneath the meter is headed **"Out-of-vocabulary terms"** (`index.html:1106`). The panel
labels the same quantity twice, once correctly and once as "drift".

The proto is honest too: `CollectionDriftStats` is documented as "Drift of indexed member content
against the current vocabulary artifact" (`opennlp_search.proto:251-253`), which is not drift over
time, it is divergence from a reference set.

---

## 3. The zero-coverage trap

FACT. With no vocabulary artifact configured, every term is "new", so
`newOccurrences == occurrences` and `vocabularyCoverage` is `0`. The UI then paints an empty meter
at `0%` (`lifecycle-workbench.ts:493-494`) with the label:

> "No vocabulary artifact is configured; every indexed term counts as new."
> (`lifecycle-workbench.ts:497`)

OPINION (P1). A bar pinned at 0% reads as "you are failing", not as "this is not being measured".
The default state of a brand-new collection is therefore an alarming-looking empty gauge. The
"New terms" and "New occurrences" rows meanwhile show large numbers that are simply the total term
counts, restated.

Fix: when `vocabularyArtifactId` is unset, hide the meter entirely and replace it with a call to
action rather than a zero. Proposed:

> **Not measured yet.** Coverage is measured against a vocabulary artifact, and this collection has
> none. Learn one on the **Trainer** tab, then paste its id above.

with a `data-workbench-jump="trainer"` button. Also suppress the "New terms" and "New occurrences"
rows in that state, since with no vocabulary they duplicate "Distinct terms" and "Occurrences"
exactly.

---

## 4. Proposed rename and flyout text

OPINION.

| Current string | Where | Proposed | Rationale |
| --- | --- | --- | --- |
| `Vocabulary drift` (heading) | `index.html:1100` | **`Vocabulary coverage`** | It is what the code computes, it is what the meter's own `aria-label` already says ("Vocabulary coverage", `index.html:1101`), and it matches the label text at `lifecycle-workbench.ts:496` which already uses the word "coverage". |
| `Report vocabulary drift after this many new terms` | `index.html:1091` | **`Alert me after this many unfamiliar terms`** | "Report ... drift" describes an internal event kind; the user wants to know when to retrain. |
| `Leave this at 0 to never report drift.` | `index.html:1094` | **`Leave this at 0 for no alerts.`** | |
| `New terms` | `lifecycle-workbench.ts:490` | **`Terms not in the vocabulary`** | "New" implies "recently added", which is not what is measured. |
| `New occurrences` | `lifecycle-workbench.ts:491` | **`Their total occurrences`** | |
| `Distinct terms` | `lifecycle-workbench.ts:488` | keep | Standard corpus-linguistics usage (types vs tokens). |
| `Occurrences` | `lifecycle-workbench.ts:489` | keep | |
| `Analysis chain` | `lifecycle-workbench.ts:492` | keep, add tooltip | See flyouts below. |
| `Drift threshold crossed: N new terms.` | `lifecycle-workbench.ts:470` | **`N terms are now outside the vocabulary. Consider retraining.`** | |
| `Out-of-vocabulary terms` | `index.html:1106` | keep | Already correct. |
| Chip tooltips `In the current vocabulary` / `Out of the current vocabulary` | `lifecycle-workbench.ts:506-507` | keep | Already correct. |

If the lead wants to keep the word "drift" because `CollectionDriftStats` and
`COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED` are already on the wire, then the P1 minimum is the
flyout below, so the word is at least defined where it is used. But note the panel is *already*
internally inconsistent, so changing the heading to "Vocabulary coverage" makes it agree with three
strings it currently contradicts.

### Flyout: what "vocabulary" means here

The owner asked for exactly this. Proposed text, to hang off the heading:

> **Vocabulary** is the fixed list of terms your model was trained on. It is produced on the
> **Trainer** tab and saved as a vocabulary artifact with an id.
>
> **Coverage** is how much of the text now in these workspaces that list still recognises. If you
> index new documents about a subject the model never saw, coverage falls and unfamiliar terms pile
> up in the list below.
>
> Low coverage is a signal, not an error: it means the model is being asked about words it does not
> know, and a retrain would probably help. Learn a new vocabulary on **Trainer**, distill a model
> from it, then use **Rebuild index** above to move these workspaces into the new model's vector
> space.

### Two smaller flyouts worth adding

- **Analysis chain**: *the exact tokenizing and normalizing steps used to count these terms. It is
  the same chain the keyword search uses, so these counts match what a search would actually match.*
- **Occurrences vs distinct terms**: *"distinct terms" counts each different word once. "Occurrences"
  counts every time any of them appears.*

---

## 5. What is NOT measured, and should probably be said out loud

FACT, from the same code path:

- Coverage counts **term membership only**. It says nothing about whether the *embeddings* are still
  appropriate. A model can be badly out of date while coverage sits at 100%, because coverage
  compares strings, not meanings.
- Coverage is recomputed live from current member content
  (`SearchCollectionRegistry.describe`), so deleting documents *raises* coverage. It is a property
  of what is in the indexes right now, not a running history.
- Sealed and startup-bundle indexes cannot be members
  (`OpenNlpSearchServiceImpl`, `SetCollection` member validation rejects startup bundles with
  "SetCollection member '<id>' is a startup bundle; members must be dynamic indexes"), so a
  collection can only ever measure workspaces still under construction.

OPINION (P2). The third point deserves a line of UI text next to the member picker, because it is
surprising: you cannot watch coverage of the corpus you actually serve, only of workspaces you are
still building.

---

## Questions for the lead

1. The proto names are `CollectionDriftStats` and `COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED`.
   Is it acceptable for the UI to say "coverage" while the wire says "drift", or should the rename
   go all the way to the proto before it ships?
2. Should the coverage meter be inverted, showing the OOV percentage rather than coverage? "12%
   unfamiliar" may motivate a retrain better than "88% covered". I lean toward keeping coverage
   because a filling bar reads as good, but it is a product call.
3. Is there any intent to add a genuine time series (coverage at model publication versus coverage
   now)? That would justify the word "drift" and would be the more useful thing to ship.
