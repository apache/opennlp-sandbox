# Analyze tab: cross-references, error states, empty states

FACT rows cite `path:line`. Measurements against the live demo are in
`../reference/demo-errors.md` and `../reference/demo-state.md`.

---

## 1. Cross-references to other tabs

### 1.1 FACT: the Analyze tab has zero outbound jumps

`data-workbench-jump` appears three times in `index.html`, all of them outside
this tab:

| Line | In tab | Target |
| --- | --- | --- |
| `index.html:555` | Corpus search | `session-search` |
| `index.html:592` | Corpus search | `workflows` |
| `index.html:735` | Workspace search | `corpus-search` |

The handler is `workbench-navigation.ts:40`. The Analyze panel
(`index.html:77-367`) contains none.

### 1.2 FACT: inbound jumps into the Analyze tab already exist

| Source | Code | Effect |
| --- | --- | --- |
| Trainer, `Use in Analyze` on a trained static model | `main.ts:244-254` | selects the model in `#embedding-model-select` and calls `workbenchNavigation.show("analysis")` |
| Workflows, `Open in Analyze` | `main.ts:361-375` | loads the response, renders every view, calls `workbenchNavigation.show("analysis")` |
| Models & data, an installed embedding model | `main.ts:212-216` | adds the model to the selector without switching tabs |

So the navigation plumbing works. Only the outbound direction is missing.

### 1.3 FACT: one existing hop is broken in the other direction

`SemanticWorkbench.openDocument` is wired at `main.ts:272-286`. It is invoked
from `Open` on a workspace search hit (`semantic-workbench.ts:425-431`), which
lives in the Workspace search tab (`#search-results`, `index.html:784`). The
callback repopulates `#analysis-text`, re-renders every result view, and calls
`selectResultTab("document")`, but it never calls
`workbenchNavigation.show("analysis")`. The user presses `Open`, nothing visible
happens, and the loaded document is waiting on a tab they are not looking at.

OPINION (P1): add `workbenchNavigation.show("analysis")` to that callback, the
way `onOpenAnalysis` at `main.ts:373` already does.

### 1.4 FACT: `Add to server workspace` reports its outcome on another tab

`#add-to-index-button` is in the Analyze result panel (`index.html:252`).
Pressing it calls `SemanticWorkbench.addCurrentDocument`
(`semantic-workbench.ts:243-265`), whose success and failure text both go to
`setStatus`, which writes `#semantic-status`. `#semantic-status` is at
`index.html:781`, inside the Workspace search panel.

So the single most consequential button on the Analyze tab writes
`Indexed by the gRPC server. 47 chunks available.` or
`Server-side indexing failed.` into a hidden element. On the Analyze tab the
press produces no feedback at all beyond the button briefly disabling.

OPINION (P1): route that outcome to `#form-status` (or a status line inside the
result panel), and add a `data-workbench-jump="session-search"` follow-up
button reading `Search this workspace` once the index exists. The help callout
already tells the user to go there in prose (`index.html:103-104`) with nothing
to click.

### 1.5 Cross-references that should exist and do not

| Situation on the Analyze tab | Where the user must go | Link today |
| --- | --- | --- |
| A feature reads `Needs model or data` in the checklist (`analysis-controls.ts:289`) | Models & data, to install a name finder / parser / chunker | none |
| `No embedding model configured` (`index.html:141`) | Models & data, to install a static embedding model; or Trainer, to distil one | none |
| Heatmap says `Enable document embeddings and at least one chunk strategy` (`semantic-workbench.ts:537`) | back to the composer, then possibly Models & data | none |
| Heatmap says `Enable Sentiment and install its model data first` (`semantic-workbench.ts:535`) | Models & data | none, and the catalog has no sentiment role anyway (`model-gating.md` 4.3) |
| `Add to server workspace` succeeded | Workspace search | prose only (`index.html:103-104`) |
| `Language pipeline` select is permanently disabled (`index.html:146`) | Models & data, to install a language pack | none |
| A batch document failed with `NOT_FOUND: ... no name finder models ...` | Models & data | none |
| The user wants to search a *persisted* corpus rather than the workspace | Corpus search | none |

OPINION (P1 for the first two, P2 for the rest): add
`data-workbench-jump="models"` buttons at the three model-gated dead ends, and
a `data-workbench-jump="session-search"` after a successful add. See
`model-gating.md` section 5 for the full brown-out design.

---

## 2. Empty states

| State | What the user sees | FACT citation | Verdict |
| --- | --- | --- | --- |
| Nothing typed | `Analyze text` is disabled; `#form-status` reads `Ready. Enter text or load the sample to begin.` | `main.ts:1161-1166`, `main.ts:457` | good |
| Whitespace only | same, because the check is `textArea.value.trim().length === 0` | `main.ts:1164` | good |
| Empty document sent by hand | 400 `document.raw_text is required` | `../reference/demo-errors.md` | not reachable from the UI |
| Analysed, no layers returned | `#annotated-text` shows the raw text; the drawer shows `This analysis returned no document-shape layers.` | `main.ts:633-636` | good, but see `terminology.md` 7.1 for the wording |
| Analysed, no document text in the response | `The response did not contain document text.` | `main.ts:630` | good |
| Chunks tab, no chunk groups | `No chunk groups were returned for this analysis.` | `chunk-projection-view.ts:38` | good |
| Chunks tab, a group with no chunks | `This strategy returned no chunks.` | `chunk-projection-view.ts:60` | good |
| Heatmap, nothing analysed | `Analyze a document to build its heatmap.` and `Analyze text to build a document heatmap.` | `semantic-workbench.ts:452`, `:530` | good |
| Heatmap, query mode, no query run yet | `Enter a query above. The gRPC server will index and score this document's chunks.` | `semantic-workbench.ts:459-460` | good |
| Heatmap, sentiment mode, no sentiment layer | `This document has no typed sentiment layer with positional scores.` | `semantic-workbench.ts:461` | good |
| Heatmap, server returned nothing | `The gRPC server returned no compatible chunks for this document.` | `semantic-workbench.ts:368` | good |
| Graph, nothing analysed | `Analyze a document to build its graph.` | `index.html:360` | good |
| Graph, a shape with no layers | `Analyze a document with typed layers to build its graph.` | `charts.ts:105` | good |
| Layer with no visible spans in this window | `This layer has no selectable text spans in the current document window.` | `main.ts:775` | good |
| Layer filter matches nothing | `0 of 16 layers`, and the selection falls back to the first visible button | `main.ts:917-948` | good |
| Batch box empty | `Analyze batch` disabled | `main.ts:390-393` | good |
| Protobuf JSON tab, nothing analysed | `No analysis yet.` | `index.html:364` | good |

FACT. The empty-state coverage on this tab is genuinely complete. Every panel
has a message and none of them are placeholders.

---

## 3. Error states

### 3.1 Service unreachable at startup

FACT. `initialize()` calls `getHealth()` first (`main.ts:419`). On failure
(`main.ts:420-429`): the status pill goes to `Unavailable`, `#service-name`
reads `Offline`, `#service-description` reads
`The web interface is running, but the analysis service could not be reached.`,
`#form-status` shows `errorMessage(error, "The analysis service is unavailable.")`,
and `serviceAvailable` stays false so `Analyze text` never enables.

FACT. There is no retry. `serviceAvailable` is set once
(`main.ts:421`, `main.ts:430`) and never re-checked. A user whose gateway comes
up thirty seconds after the page must reload by hand.

OPINION (P2): add a `Retry connection` button beside the offline description
that re-runs `initialize()`.

### 3.2 Partial discovery

FACT. `getServiceInfo()` and `getModelBundles()` run through
`Promise.allSettled` (`main.ts:436`). If either rejects, the description becomes
`Connected. Some discovery information is not currently available.` and the
status becomes `Connected with limited discovery. Automatic configuration is
still available.` (`main.ts:452-457`).

FACT. In that state `capabilities.maxSteps` is empty, so
`AnalysisControls.request` silently downgrades the preset from `max` to
`automatic` (`analysis-controls.ts:150-152`) while the select still reads
`All available features`. The label and the request disagree.

OPINION (P2): when `maxSteps` is empty, disable the `All available features`
option and select `Server automatic` so the control shows what will be sent.

### 3.3 Network failure during an analysis

FACT. `submitAnalysis` catches everything (`main.ts:559-568`): it clears the
stored response, disables the three export buttons, hides the X-ray, writes
`The analysis request did not complete.` into `#response-output`, and puts
`errorMessage(error, "Analysis failed. Please try again.")` into `#form-status`.

FACT. `errorMessage` (`ui-utils.ts:65-67`) returns `error.message` whenever the
error is an `Error` with a message. A `fetch` network failure is a `TypeError`
with message `Failed to fetch` (Chromium) or `NetworkError when attempting to
fetch resource.` (Firefox). So the user sees the raw browser string, not the
fallback.

OPINION (P2): in `api.ts`, wrap the `fetcher` call so a thrown `TypeError`
becomes `Could not reach the analysis service. Check that the gateway is
running.` The gateway-authored messages should still pass through untouched;
only transport failures need translating.

### 3.4 Very large documents

FACT, measured (`../reference/demo-state.md`):

| Sample | Text | Response | Wall time | Annotations |
| --- | --- | --- | --- | --- |
| `Use short sample` | 200 chars | ~40 KB | under 1 s | ~200 |
| `Load Alice novel` | 144,569 chars | **75,370,306 bytes** | 5.1 s | 286,938 |
| `Load Pride and Prejudice` | 685,954 chars | **322,976,205 bytes** | 20.0 s | not counted |

All three runs used the exact request the tab builds for `All available
features` plus `minilm-gpu` plus `Sentence chunks`.

FACT. The gateway scales its unary deadline with input size.
`GrpcAnalysisRpc.analyze` calls `sizedDeadlineStub(request.getDocument()
.getRawTextBytes().size())` (`GrpcAnalysisRpc.java:144-147`), and
`scaledDeadlineNanos` (`GrpcAnalysisRpc.java:239-244`) returns
`min(ceiling, base + perMebibyte * bytes / 1 MiB)`. With the shipped defaults,
`--request-timeout-seconds 30` (`OpenNlpGrpcWebApp.java:72-74`),
`--request-timeout-per-megabyte-seconds 120` (`:76-80`),
`--long-running-timeout-seconds 1800` (`:82-84`), Alice gets about 46 seconds
and Pride and Prejudice about 109 seconds. Both measured runs finished well
inside that. This part works.

FACT. What is not bounded is the browser side.

1. **The response is downloaded and parsed in full.** `analyze()` goes through
   `requestJson` (`api.ts:534-544`), which calls `response.json()`. A 323 MB
   body becomes a JavaScript object graph in the tab. There is no size check
   anywhere in `api.ts` and no warning before the request.
2. **The Protobuf JSON tab is guarded, the export buttons are not.**
   `jsonPresentation` (`json-response.ts:31-41`) refuses to stringify when the
   text exceeds 100,000 characters or the annotations exceed 100,000, and shows
   `This response is too large to format safely in the browser. Use Copy JSON or
   Download JSON when you need the complete protobuf JSON.` Both novels trip
   this. But `storedJson()` (`main.ts:1152-1154`) then falls back to
   `JSON.stringify(currentResponse)`, so `Copy JSON` and `Download JSON`
   stringify the full 75 MB or 323 MB anyway. The message tells the user to
   press exactly the buttons that will hurt.
3. **`Download .pb` cannot work on Pride and Prejudice.**
   `downloadResponsePb` (`main.ts:1034-1046`) POSTs `storedJson()` to
   `/api/v1/response/encode`. The gateway caps request bodies at
   `--max-request-bytes 104857600` (`OpenNlpGrpcWebApp.java:86-88`) and rejects
   larger ones with HTTP 413 and the body
   `{"code":"RESOURCE_EXHAUSTED","message":"HTTP request body exceeds 104857600 bytes"}`
   (`OpenNlpGrpcWebServer.java:219-220`). A 323 MB payload is over three times
   the cap. `errorMessage(error, "The .pb download did not complete.")` will
   surface the raw byte count to the user.
4. **The graph and the document window are bounded, correctly.**
   `supportsCompleteGraph` refuses above 5,000 annotations
   (`document-window.ts:45-47`), so both novels get the 120-node overview and
   the button reads `Complete graph limited for large documents`
   (`semantic-workbench.ts:579`). `documentWindow` pages the text at 16,000
   characters (`document-window.ts:20`, `:30-36`), so Alice renders as 10
   windows. Good.
5. **The all-layer overlay is not bounded.** `combinedAnnotationSegments`
   (`document-shape.ts:115-149`) builds a boundary map over every annotation in
   every layer before any windowing happens. For Alice that is 286,938
   annotations; for Pride and Prejudice roughly five times that. The result is
   cached (`main.ts:865-880`) but the first `All annotations` click pays the
   whole cost, and `renderCombinedOverlay` then walks the entire segment array
   on every window change (`main.ts:823-851`). The default overlay is
   `Highlights`, which is far cheaper, so this only bites on an explicit click.

OPINION (P1): before sending a document above some threshold (`maxTextBytes` is
already discovered but never used, `analysis-config.ts:213`), warn in
`#form-status` that the response will be large and offer to drop `Document
embeddings`, which is what actually produces the hundreds of megabytes.
Removing embeddings took Alice from 75 MB to 45 MB in the measured runs, and it
is the chunk vectors that dominate.

OPINION (P1): make `Copy JSON` and `Download .pb` respect the same threshold
`jsonPresentation` uses. When the response is over the limit, `Download JSON`
should stream the raw response body to a blob rather than re-stringifying, and
`Download .pb` should be disabled with the reason
`This response is too large to transcode. Use Download JSON.`

OPINION (P2): show the response size next to `Layers` / `Annotations` /
`Offsets` in the result summary. The number is the single most useful thing to
know after analysing a novel and it is free to compute.

### 3.5 Proto decode failure

FACT. `Open saved response` accepts `.json` and `.pb`
(`index.html:256-257`). `loadLocalResponse` (`main.ts:1057-1069`) routes `.pb`
through `decodeAnalyzeResponsePb`, which POSTs the bytes to
`/api/v1/response/decode` (`api.ts:507-519`).

FACT, reproduced:

| Input | HTTP | Message the user sees in `#form-status` |
| --- | --- | --- |
| junk bytes | 400 | `Malformed protobuf response bytes: Protocol message tag had invalid wire type.` |
| a valid `.pb` renamed from another message type | 400 | same family of message |
| **a zero-byte file** | **200** | nothing; the tab reports `Loaded empty.pb` and shows `The response did not contain document text.` |

The zero-byte case is the sharp edge: a truncated or empty `.pb` decodes to
`{}`, the gateway returns 200, and the tab presents an empty analysis as a
success. `presentLoadedResponse` (`main.ts:1071-1083`) does not check that the
decoded value has a document.

OPINION (P2): after decoding, if `readDocumentShape(response).rawText` is empty
and there are no layers, report
`<name> did not contain an analysis response.` as an error instead of
`Loaded <name>.`

### 3.6 `Failed to read message.`

FACT. The literal string `Failed to read message.` exists nowhere in this
repository. It is produced by grpc-java, in
`io/grpc/internal/ClientCallImpl$ClientStreamListenerImpl$1MessagesAvailable`
(verified by scanning the class in
`io.grpc:grpc-core:1.81.0`). grpc-java raises it as `Status.CANCELLED` when a
client stub cannot deframe or parse a response message, most often because the
message exceeded the channel's max inbound size.

FACT. The gateway sets that limit to 100 MiB,
`DEFAULT_GRPC_MAX_INBOUND_MESSAGE_BYTES` at `OpenNlpGrpcWebApp.java:192`,
applied at `:210`, well above grpc's 4 MiB default. `GrpcAnalysisRpcTest`
covers the raise with `acceptsResponsesBeyondTheGrpcDefaultMessageLimit`. The
measured Pride and Prejudice response is 323 MB as JSON; as protobuf it is
smaller, but the headroom over 100 MiB is not large.

FACT. If it does fire, `GrpcJsonApi.java:194-203` passes the description through
unchanged, `GrpcHttpStatusMapper.java:44` maps `CANCELLED` to HTTP 499, and
`responseError` (`api.ts:546-560`) lifts `message` into the `Error`. The user
sees the bare sentence `Failed to read message.` in `#form-status` with no
context.

OPINION (P2): the gateway should not forward a transport-layer description
verbatim. Catch `CANCELLED` and `RESOURCE_EXHAUSTED` from the analysis stubs and
substitute a message that names the cause and the fix, for example
`The analysis response was too large for the gateway to read. Reduce the
document size or disable document embeddings.` The raw gRPC description can go
to the log.

### 3.7 Batch stream failures

FACT. Two layers, both correct. A failure of the whole call surfaces as
`batchStatus.textContent = errorMessage(error, "The batch stream failed.")`
(`main.ts:531`). A per-document failure arrives as an NDJSON line carrying
`error.code` and `error.message` and renders as
`Document 2: GRPC_STATUS_CODE_INVALID_ARGUMENT: document.raw_text is required`
(`batch-analysis.ts:85-90`, reproduced in `../reference/demo-errors.md`).

OPINION (P3): `GRPC_STATUS_CODE_INVALID_ARGUMENT` is an enum name, not a
sentence. Map it to `Invalid input` and keep the message.

---

## 4. Correctness defect found while testing

### 4.1 FACT: the Sentiment heatmap colours negative sentences green

`buildHeatmapRows` (`visualization-data.ts:68-82`) turns sentiment annotations
into signed scores through `signedSentimentScore`
(`visualization-data.ts:162-174`):

```ts
function signedSentimentScore(label: string, score: number): number {
  const category = asciiLowerCase(label);
  const magnitude = Math.min(1, Math.abs(score));
  if (category.includes("negative")) { return -magnitude; }
  if (category.includes("neutral"))  { return 0; }
  if (category.includes("positive")) { return magnitude; }
  return Math.max(-1, Math.min(1, score));
}
```

The demo's configured sentiment model, `sst2` (`../reference/demo-state.md`),
does not emit `positive` / `negative` / `neutral`. It emits star buckets.
Measured (`../reference/demo-errors.md`):

```
"This was the worst experience of my entire life."  label "1_star",  score 0.884
"I hated every second of it."                       label "1_star",  score 0.554
"But the dessert was lovely."                       label "3_stars", score 0.424
```

None of the three labels matches any branch, so each falls to the final line and
keeps its **confidence** as its **polarity**. The most negative sentence in the
document gets `+0.884`, which `scoreColor` renders at the green end of the
diverging scale (`document-heatmap-view.ts:159-167`,
`search-view-model.ts` `scoreColor`). The heatmap tells the user that
"This was the worst experience of my entire life." is the most positive sentence
on the page.

OPINION (P1): stop inferring polarity from a free-text label. Options, in order
of preference:

1. Have the service report polarity as a typed field on the sentiment
   annotation, and read that.
2. Recognise the ordinal label families the shipped models actually use, at
   minimum `N_star` / `N_stars` (map 1 and 2 to negative, 3 to neutral, 4 and 5
   to positive), `LABEL_0`/`LABEL_1`, and `pos`/`neg`.
3. When the label matches nothing known, render the lane in a **sequential**
   palette keyed to confidence with the label printed on each segment, and say
   so in `#heatmap-status`, rather than pretending an unsigned confidence is a
   polarity.

Whichever route is taken, the fallback `return Math.max(-1, Math.min(1, score))`
must go: a probability in `[0, 1]` can never be a signed polarity.

### 4.2 FACT: a named profile silently ignores the embedding-model selection for the document centroid

`buildAnalysisRequest` sets `options.embeddingModelId` and
`options.includeDocumentCentroid` only inside the `max`/`custom` branch
(`analysis-config.ts:245-248`). In `profile` mode the selected model still
reaches the chunk configs (`analysis-config.ts:371-373`) but not the document
options, so `Profile: en-embed` plus a hand-picked model produces chunk vectors
from the chosen model and document vectors from the profile's own model, with
no centroid. Nothing on screen says so.

OPINION (P2): either apply the selection in profile mode too, or disable
`#embedding-model-select` when a named profile is chosen with the help text
`The server profile chooses its own embedding model.`

---

## 5. Recommendation summary

| Priority | Change | Files |
| --- | --- | --- |
| P1 | Sentiment heatmap polarity: stop reading polarity out of an arbitrary label | `visualization-data.ts:162-174` |
| P1 | `Add to server workspace` must report its outcome on the Analyze tab, and offer a jump to Workspace search | `semantic-workbench.ts:243-265`, `index.html:252` |
| P1 | `Open` on a workspace hit must switch to the Analyze tab | `main.ts:272-286` |
| P1 | Add `data-workbench-jump="models"` at the model-gated dead ends | `index.html:141`, `analysis-controls.ts:289`, `semantic-workbench.ts:535,537` |
| P1 | Bound `Copy JSON` / `Download .pb` by the same threshold `jsonPresentation` uses | `main.ts:1034-1046,1141-1143`, `json-response.ts` |
| P1 | Warn before analysing a novel-sized document with embeddings enabled | `main.ts:545`, using `capabilities.maxTextBytes` at `analysis-config.ts:213` |
| P2 | Translate transport-level `fetch` failures instead of showing `Failed to fetch` | `api.ts:534-560` |
| P2 | Do not forward grpc's `Failed to read message.` verbatim | `GrpcJsonApi.java:194-203` |
| P2 | Treat a `.pb` that decodes to `{}` as a load failure | `main.ts:1071-1083` |
| P2 | `Retry connection` after a failed startup health check | `main.ts:420-429` |
| P2 | When `maxSteps` is empty, do not leave the preset reading `All available features` | `analysis-controls.ts:150-152` |
| P2 | Named profile plus embedding model: apply it or disable the select | `analysis-config.ts:245-248` |
| P2 | Show response size in the result summary | `index.html:278-282`, `main.ts:1140-1151` |
| P3 | `GRPC_STATUS_CODE_INVALID_ARGUMENT` -> `Invalid input` in batch results | `batch-analysis.ts:85-90` |

---

## Questions for the lead

1. Is a 323 MB analysis response an acceptable outcome of a two-click demo path
   (`Load Pride and Prejudice`, `Analyze text`), or should the tab cap what it
   requests for the bundled novels? Dropping `Document embeddings` for inputs
   over, say, 100 KB would keep the demo honest and the browser alive.
2. Should the sentiment polarity mapping live in the front end at all, or should
   the service emit a signed polarity alongside the label? The second is the
   only version that survives a model swap.
3. `Add to server workspace` and the workspace search results are one class
   spanning two tabs (`semantic-workbench.ts`). Is that split intended, or
   should the add button move to Workspace search and the Analyze tab get a
   jump instead?
