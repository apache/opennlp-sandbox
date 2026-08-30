# Gating, empty and error states, cross-tab links, and test coverage

All quoted strings are exact. FACT rows cite the file that produces them;
OPINION rows carry a P1/P2/P3 priority.

## 1. FACT: what the tab shows on the live demo node

The live node returns `model-catalog` **without an `installsEnabled` field**, so
`readModelCatalog` sets it to `false` (`src/model-data-workbench.ts:574`), and
`installed-models` returns `{}`. The tab therefore renders:

| Region | Element id | Exact text today |
| --- | --- | --- |
| Header summary | `resource-summary` | `"10 of 17 features ready"` (`src/model-data-workbench.ts:266`) |
| Pipeline readiness | `resource-feature-list` | 10 rows `"Ready"`, 7 rows `"Needs model or data"` (`src/model-data-workbench.ts:251`) |
| Loaded bundles | `resource-bundle-list` | two `<li>` reading the raw ids `"en-sentiment"` and `"en-basic"` (`src/model-data-workbench.ts:261`, ids come from `options()` at `src/analysis-config.ts:403`) |
| Catalog | `resource-model-catalog` | 17 cards, every consent checkbox `disabled` |
| Downloaded on this node | `resource-installed-models` | `"No catalog models have been downloaded to this node."` (`src/model-data-workbench.ts:198`) |
| Install status | `resource-install-status` | `"Catalog browsing is available. Configure model.catalog_root to enable node downloads."` (`src/model-data-workbench.ts:349`) |

**P1.** The reason every card is unusable is printed once, at the bottom of the
page, inside a different card ("Verified resource installer",
`index.html:850`). The cards themselves still show a live-looking checkbox
labelled `"I reviewed Apache-2.0 and approve this node download."` and a button
reading `"Download and activate"`, both silently dead. A browned-out card
should carry its own reason, for example: `"Downloads are disabled on this node.
Set model.catalog_root in the server configuration and restart."`

**P3.** `"Loaded bundles"` lists raw bundle ids. The same payload already
carries every member's `componentType`, `backendId` (`opennlp-me`, `cuda`) and
`embeddingDimension`, and `discoverAnalysisCapabilities` throws all of it away
(`src/analysis-config.ts:147-175`). A bundle row could read
`"en-basic: language detect, sentences, tokens, POS, lemmas, embeddings
(minilm-gpu, 384d, cuda)"`.

## 2. FACT: every empty and error state, with the exact text

| Condition | Where it is produced | What the user sees |
| --- | --- | --- |
| Catalog or installed-models request fails | `initialize()` catch, `src/model-data-workbench.ts:185` | The whole `resource-model-catalog` div is replaced by the server message, or `"Could not load the model catalog."` if the error carries none, and gets the `is-error` class. |
| ... side effect of the same catch | same | `resource-installed-models` is never touched, so it stays on its placeholder `"Loading the verified model inventory."` (`index.html:846`) forever. **P2**: the catch should clear both panels. |
| Build has no catalog provider jar | `renderCatalog`, `src/model-data-workbench.ts:347` | `"This build does not publish a standard model catalog."` (server-side counterpart at `CatalogModelStore.java:335`: `"Unknown catalog_id '<id>'; the model catalog is empty because no catalog provider (opennlp-grpc-installer) is on the classpath"`). |
| `model.catalog_root` unset | `src/model-data-workbench.ts:349` and `OpenNlpModelTrainingServiceImpl.java:175` | UI: the status line above. A forced install returns `FAILED_PRECONDITION` `"model.catalog_root is not configured; catalog installation is disabled"`. |
| Download failure, SHA-256 mismatch, disk full, or bad archive layout | every `IOException` path in `CatalogModelStore` is caught at `OpenNlpModelTrainingServiceImpl.java:212` | All of them collapse to one string: **`"Catalog model installation failed"`**. The real cause (`"Catalog file failed size or SHA-256 verification: <file>"`, `CatalogModelStore.java:449`; `"Catalog model contains a symbolic link"`, `:479`; `"Catalog root does not support atomic model publication"`, `:311`) goes only to the server log. |
| Two installs at once | `ConcurrentModelInstallException`, `CatalogModelStore.java:243` | `"another model installation is active"` (RESOURCE_EXHAUSTED). The FE's own `#busy` flag hides this within one tab, so it only appears with two tabs or two operators. |
| Model already installed | `CatalogModelStore.java:260` | `"Catalog model '<id>' is already installed"`. Only reachable from a stale page, since an installed card hides its button. |
| Wrong revision or license echoed back | `CatalogModelStore.java:343,346` | `"revision does not match the immutable catalog entry"` / `"license_name does not match the immutable catalog entry"`. |
| Static table loads at the wrong width | `CatalogModelStore.java:397` | `"Loaded static embedding dimension <n> does not match catalog dimension <m>"`, delivered as the generic `"Catalog model installation failed"`. |
| Restart required, single model | `install()`, `src/model-data-workbench.ts:495` | `"<name> is installed; restart required before it becomes active."` |
| Restart required, language pack | `installPack()`, `src/model-data-workbench.ts:470` | `"The German language pack is installed; restart the server to activate the 'de' pipeline."` |
| Restart required, per card | `src/model-data-workbench.ts:214,321,390` | `"Installed, restart required"` |
| Copy button | `copyCommand()`, `src/model-data-workbench.ts:517,519` | `"Installer command copied. Replace the source, checksum, and target values."` or `"Could not copy the installer command."` |

**P1, disk space.** Nothing anywhere checks free space. Three catalog entries
are over 100 MiB and two are near half a gigabyte
(`paraphrase-multilingual-minilm-l12-v2-teacher` at 457.2 MiB,
`potion-multilingual-128m` at 506.4 MiB). No API reports free space on the
catalog root, and a full disk surfaces as `"Catalog model installation failed"`.
Minimum fix: put the download size in the button, for example
`"Download and activate (506.4 MiB)"`, and add a free-space figure to
`ListInstalledModelsResponse`.

**P1, SHA mismatch.** A checksum failure is a security-relevant event and it
currently reads identically to a flaky network. Recommend a distinct status
code and text, for example `"The downloaded file did not match the pinned
SHA-256 for <file>. Nothing was installed."`

**P2, restart banner.** There is no banner. `"Installed, restart required"`
appears per card and per pack member, but the tab never says how to restart, and
after a page reload the only remaining trace is that per-card text: the header
still says `"N of 17 features ready"` with the old N. Recommend a persistent
banner above the catalog whenever any installed model has `loaded == false`:
`"1 installed model is waiting for a server restart. Restart the OpenNLP server
to activate: GUM CC BY 4.0 English parser."`

**P1, an install can break the next boot.** `CatalogModelBootstrap` refuses to
start when a pipeline slot is claimed twice:
`"model.pipeline.de.tokenizer.path is already configured; a server serves one
model per language pipeline slot, so uninstall the other model or remove the
operator setting"` (`CatalogModelBootstrap.java:143`). The UI offers the German
pack with no hint that an operator-configured German tokenizer already exists,
so a successful-looking install can prevent the next start. The catalog response
would need to report the currently configured slot occupants for the FE to warn.

## 3. FACT: cross-tab links

`index.html` contains exactly three `data-workbench-jump` attributes:

| Line | Target | Context |
| --- | --- | --- |
| `index.html:555` | `session-search` | "Workspace ..." link in the analysis workbench |
| `index.html:592` | `workflows` | "build your own workspace index" |
| `index.html:735` | `corpus-search` | "Corpus ..." link |

**None of them targets `models`, and the Models & data tab emits none.** The
mechanism exists and works (`src/workbench-navigation.ts:40`), it is simply
unused here.

Jumps that should exist, into this tab:

| From | Trigger | Text today | Priority |
| --- | --- | --- | --- |
| Analyze, feature checkbox | step supported but not configured | `"Needs model or data"` (`src/analysis-controls.ts:289`), disabled, no link | P1 |
| Trainer, step 3 | no teacher configured | `"No teachers are configured; add training.teacher entries."` (`src/vocabulary-trainer.ts:165`) and `"No teachers configured"` in the select (`:157`), even though installing `all-minilm-l6-v2-teacher` from this tab is the supported way to get one | P1 |
| Trainer, step 3 | writes disabled | `"Training is disabled: the server has no vocabulary artifact root or no teachers."` (`src/vocabulary-trainer.ts:161`) | P2 |
| Workspace search / Workflows | no embedding model to pick | see the search themes | P2 |

Jumps that should exist, out of this tab:

| Trigger | Suggested destination | Priority |
| --- | --- | --- |
| A `STATIC_EMBEDDING` install finishes and `loaded == true` | Analyze, with that model preselected. The callback `onEmbeddingModelInstalled` (`src/main.ts:212`) already registers the model, but the user is left on the Models tab with the status line `"Potion Base 8M is installed and active on this server node."` | P1 |
| A `DISTILLATION_TEACHER` install finishes | Trainer. `onTeacherInstalled` (`src/main.ts:216`) already re-initializes the trainer silently. | P1 |
| A "Ready" readiness row | Analyze, with that step preselected | P2 |
| A "Needs model or data" row that the catalog can fix | scroll to the catalog card that fixes it | P1 |

## 4. FACT: data available for gating decisions

For any feature, the FE can already decide *whether* it is available:
`service-info.supportedSteps` (in this build?) intersected with the steps
configured by `model-bundles` plus `service-info.configuredResources`
(`src/analysis-config.ts:136-215`).

What the FE **cannot** decide today is *which catalog entry would fix it*:

- `ModelCatalogDescriptor` has no step list, so the mapping role to step has to
  be hard-coded client-side (see `findings/unlocks-and-tags.md`).
- `ListModelBundles` reports which steps are configured but not which
  configuration key or model file supplies each one, so a "this is already
  covered by X" message is impossible.
- `configuredResources` in `service-info` names resource kinds
  (`STANDARD_RESOURCE_SUBWORD_MODEL`, `STANDARD_RESOURCE_WORDNET_LEXICON`) that
  no `ModelArtifactRole` covers, so a readiness row can be red with no catalog
  answer and the UI cannot tell the user that.

Gap list, in one line each:

1. No `unlocked_steps` on the catalog descriptor.
2. No format or file list on the catalog descriptor (see the tags document).
3. No `requires_restart` on the descriptor; the FE duplicates the Java rule at
   `src/model-data-workbench.ts:640`.
4. No free-space or catalog-root path in `ListInstalledModelsResponse`.
5. No "which model currently occupies this slot" in either response, so the
   double-configuration boot failure cannot be pre-empted.

## 5. FACT: tests that exercise this tab

Front-end unit tests, `opennlp-grpc-webapp-default/test/model-data-workbench.test.ts`:

| Test | Covers |
| --- | --- |
| `reads every first-class catalog artifact role` (:76) | `readModelCatalog` for 8 roles, and `readInstalledModels` |
| `rejects catalog cards that cannot safely support informed consent` (:140) | `requiredHttpsUri` |
| `groups the four pipeline roles sharing one model id into a language pack` (:157) | `groupCatalogPacks` |
| `keeps an incomplete pipeline group as single cards` (:172) | `groupCatalogPacks` negative case |
| `names languages for people and falls back to the raw code` (:179) | `languageDisplayName` |
| `requires license acknowledgement before installing and activates static models` (:211) | consent gate, `onEmbeddingModelInstalled` |
| `publishes static models restored from the node inventory` (:244) | `publishInstalledStaticModels` |
| `explains that a newly installed parser needs a server restart` (:264) | `restartRole` for `parser` |
| `renders the verified downloaded-model inventory` (:297) | `renderInstalledModels` |
| `installs a whole language pack behind one license review` (:321) | `installPack` |

Other front-end tests: `test/api.test.ts:212` asserts `/api/v1/model-catalog`
and `/api/v1/installed-models` are GET paths, and `:218`
`streams model download progress and resolves with the installed descriptor`
covers `installModel` NDJSON parsing.
`test/analysis-config.test.ts` covers `discoverAnalysisCapabilities`.
`test/workbench-navigation.test.ts:84` covers the one existing jump
(`workflows`).

Gateway tests: `GrpcJsonVocabularyApiTest.java:117-119` asserts
`GET /api/v1/model-catalog` and `GET /api/v1/installed-models` succeed against
an empty training RPC; `OpenNlpGrpcWebServerTest.java:190` exercises
`POST /api/v1/install-model`.

Service tests: `CatalogModelStoreTest` (14 tests, including
`installsVerifiesPublishesAndReloadsAStaticEmbeddingModel`,
`parserAndChunkerInstallationsWaitForAValidatedRestart`,
`consentAndPinnedIdentityAreRequiredBeforeAnyDownload`,
`startupRejectsTamperedInstalledBytes`,
`rejectsAStaticTableWhoseLoadedDimensionContradictsTheCatalog`,
`onlyOneCatalogInstallationRunsAtATime`,
`servesAnEmptyCatalogWithoutTheInstallerAddOn`) and `CatalogModelBootstrapTest`
(8 tests, including `refusesASecondModelForOneLanguagePipelineSlot` and
`addsVerifiedNameFinderPathsKeyedByEntityType`).

Installer tests: `StandardModelCatalogTest`
(`catalogsPinnedEmbeddingParserAndChunkerModels`,
`everyCatalogFileHasAnExactSizeAndSha256`,
`offersTheSevenClassicEnglishNameFinders`,
`everyCatalogIdUsesOnlyLowercaseLettersDigitsAndHyphens`,
`registersThroughTheCatalogSpi`) and `UdLanguageModelCatalogTest` (2 tests).

The service and installer sides are well covered. The gaps are all in the front
end.

## 6. FACT: features on this tab with no test

| Untested behaviour | Function or element |
| --- | --- |
| The whole "Pipeline readiness" grid and its three states | `ModelDataWorkbench.configure`, `src/model-data-workbench.ts:241`. No test constructs the workbench and calls `configure`; `test/analysis-controls.test.ts` calls a different class's `configure`. |
| The header summary string `"N of 17 features ready"` | `src/model-data-workbench.ts:266` |
| "Loaded bundles" rendering, including `"No model bundles are currently loaded."` | `src/model-data-workbench.ts:265` |
| The `installsEnabled === false` path: disabled checkboxes plus `"Catalog browsing is available. Configure model.catalog_root to enable node downloads."` | `src/model-data-workbench.ts:337,349`. Every test passes `installsEnabled: true` (`test/model-data-workbench.test.ts:200,275,325`). **This is the state the live demo node is actually in.** |
| Empty catalog: `"This build does not publish a standard model catalog."` | `src/model-data-workbench.ts:347` |
| Load failure: `"Could not load the model catalog."` and the `is-error` class | `src/model-data-workbench.ts:185` |
| `MODEL_ARTIFACT_ROLE_NAME_FINDER` end to end | The role list in `test/model-data-workbench.test.ts:76` covers 8 of the 9 roles and omits `name-finder`. That is why the missing `roleLabel` entry (`src/model-data-workbench.ts:653`), which makes the chip render the raw string `"name-finder"`, is undetected. |
| `onTeacherInstalled` firing after a teacher install | Passed as `vi.fn()` at `test/model-data-workbench.test.ts:216,256,285,308,336` and never asserted. |
| The copy button `copy-resource-command` and both of its status strings | `copyCommand()`, `src/model-data-workbench.ts:514` |
| Install failure status: `errorMessage(error, "Could not install <name>.")` | `src/model-data-workbench.ts:503`; the pack variant at `:474` |
| Pack partial install: the button text `"Install the remaining N"` | `src/model-data-workbench.ts:435` |
| `byteLabel` unit boundaries (bytes / KiB / MiB) | `src/model-data-workbench.ts:630` |
| `"Artifact hash unavailable"` fallback | `src/model-data-workbench.ts:220` |
| Everything, at the e2e level | `e2e/workbench.spec.ts`, `e2e/analysis.spec.ts`, and `e2e/corpus-search.spec.ts` contain no reference to the Models & data tab, `models-workbench-tab`, or any `resource-*` element. |

## Questions for the lead

1. Should the browned-out catalog card explain `model.catalog_root` to an end
   user at all, or should a node without a catalog root hide the catalog section
   entirely and show one operator-facing notice?
2. Is a restart something the workbench can trigger, or is the banner purely
   informational? That changes whether the copy is "Restart the server" or
   "Ask your operator to restart the server".
3. The generic `"Catalog model installation failed"` is deliberate (the detailed
   cause is logged). Is surfacing the specific failure class, without paths,
   acceptable, for example `SHA-256 mismatch`, `download failed`,
   `not enough disk space`?
