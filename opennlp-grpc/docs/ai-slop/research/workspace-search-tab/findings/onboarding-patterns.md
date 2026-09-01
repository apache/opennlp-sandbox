# Teaching "workspace" to a first-time user: which pattern, and why

The owner's follow-up question was "what do common websites do to help people? I'm not a
FE dev". This file answers that in the order a non-front-end reader needs: what the
options are, what the industry says about each, what this app already has, and the one
recommendation with the exact markup.

Sources are excerpted in `reference/onboarding-empty-states.md`,
`reference/onboarding-design-systems.md`, and
`reference/onboarding-inline-help-and-tooltips.md`, all fetched 2026-08-28.

## 1. What this app already has (FACT)

Counted in `opennlp-grpc-webapp-default/index.html`:

| Affordance | Count | Where the CSS lives |
|---|---|---|
| `<details>` disclosures | 10, of which 6 are `class="help-callout"` | style.css:520-527 |
| `<small class="field-help">` helper text under a control | 11 | style.css (`.field-help`) |
| `<p class="tab-bridge">` cross-tab paragraphs | 2 (index.html:553, 733) | style.css:476 |
| `.link-button` with `data-workbench-jump` | 3 (index.html:555, 592, 735) | style.css:477-478 |
| `<p class="empty-message">` placeholders | 11 | style.css:686 |
| `aria-live` status regions | 25 | n/a |
| `.visually-hidden` labels | 8 | style.css:1188 |
| an accessible modal drawer, hand-rolled | 1 (`#annotation-details`, index.html:369-370) | annotation-drawer.ts:38-60 |

FACT: `package.json` lists exactly one runtime dependency, `echarts`. There is no UI
framework, no tooltip library, no tour library. Everything above is plain HTML, plain
CSS, and about 40 lines of TypeScript for the drawer.

This matters for the recommendation: the two patterns that need the least new code are
the two the app already ships.

## 2. The six patterns, ranked for this app

Ranking criterion: how much a plain Vite plus vanilla-TypeScript app has to build, versus
how much of the owner's problem it solves.

### Rank 1: first-run empty state with one primary action

**What it is.** Atlassian's one-line definition: "An empty state appears when there is no
data to display and describes what the user can do next."
(`reference/onboarding-design-systems.md`, section 6.)

**Why it is first.** The Nielsen Norman Group guideline headings are, verbatim:
"Use Empty States to Communicate System Status", "Use Empty States to Provide Learning
Cues", and "Use Empty States to Provide Direct Pathways for Key Tasks"
(`reference/onboarding-empty-states.md`). That is precisely the three things this tab
fails to do today (findings/gating-and-links.md section 2).

**The single most on-point published rule for this exact problem** is Carbon's, verbatim:

> Don't use product-specific terms that the user may not yet understand.

and

> Don't cover multiple options in one empty state. If there are multiple things a user can
> do, pick the most important and keep the focus on that action.

Current text, index.html:782: `Analyze an embedding-enabled document, then add it to the
server workspace.` That sentence breaks the first rule twice ("embedding-enabled",
"workspace") and offers no action at all.

**The shipped example to copy.** Grafana ships two different empty-state shapes and
chooses between them by whether the concept needs teaching. For "dashboard", a word every
user knows, the empty state is a title plus a button:
`You haven't created any dashboards yet` / `Create dashboard`. For "annotation", a word
they may not know, it is a title, a full definition paragraph, a docs link, and one
button:

> Annotations provide a way to integrate event data into your graphs. They are visualized
> as vertical lines and icons on all graph panels...

then `Checkout the Annotations documentation for more information.` and
`Add annotation query`. Strings quoted from `public/locales/en-US/grafana.json`, see
`reference/onboarding-empty-states.md`.

"Workspace" is an "annotation", not a "dashboard". It needs the four-part shape.

**Cost here:** replace one `<p class="empty-message">` and add one `.link-button` with
`data-workbench-jump="analysis"`. No new CSS, no new JavaScript, because
workbench-navigation.ts:40-42 already binds every `[data-workbench-jump]` on the page at
construction time.

**When NOT to use:** Carbon warns against over-teaching secondary features:
"save the more involved educational moments for primary features and more complex
situations." This tab is a primary feature, so the teaching version is right here, but
the same treatment should not be sprayed across every panel.

### Rank 2: an inline `<details>` disclosure titled "What is a workspace?"

**What it is.** Carbon calls the family "disclosures": "moments that open up on a page and
reveal additional information related to the source it is triggered from. Unlike tooltips,
the content expanded by a disclosure may contain interactive elements."
(`reference/onboarding-design-systems.md`, section 4.)

**Why it is second.** NN/g's headline finding on contextual help is
"prefer pull over push":

> Tutorials interrupt users, don't necessarily improve task performance, and are quickly
> forgotten. Contextual help signals can avoid these pitfalls but require unintrusive ways
> to activate.

and their first rule is "Make it easy to dismiss (and recall) the help content"
(`reference/onboarding-inline-help-and-tooltips.md`, section 1). A native
`<details>` satisfies both by construction: it is closed until asked for, it reopens on
demand, and it is in the tab order for free.

**Accessibility, and this is the decisive argument.** WCAG 2.2 SC 1.4.13 imposes three
obligations (dismissible, hoverable, persistent) on any content that appears on hover or
focus. A click-activated native disclosure is not covered by that criterion at all, so
choosing `<details>` removes three separate conformance obligations that a hand-rolled
tooltip would create (`reference/onboarding-inline-help-and-tooltips.md`, section 7).

**Cost here:** one `<details class="help-callout">` block. The class already exists and is
used six times on this page. Zero new CSS, zero new JavaScript.

**Carbon's constraints to respect:** "Do not nest one disclosure within another
disclosure", "only one should open at a time", and "Do not hide important information
inside of a disclosure that the user may need in order to complete a task". So the
*definition* goes in the disclosure; the *call to action* stays visible in the empty
state.

Practical note for the implementer: the "only one open at a time" rule is enforceable
with no script. Giving several `<details>` elements the same `name` attribute makes the
browser treat them as an exclusive accordion, so opening one closes its siblings. See
`reference/onboarding-inline-help-and-tooltips.md`. If that is used, the two callouts on
this tab ("What is a workspace?" and "How to use workspace search") should share one
name.

Exact proposed markup is in findings/what-is-a-workspace.md section 5.2.

### Rank 3: always-visible helper text under a control

**What it is.** The `<small class="field-help">` the app already uses 11 times, for
example index.html:1012-1015 on the Lifecycle tab:

> **Save checkpoint** writes the workspace to disk, and it keeps accepting new documents.
> **Seal as read-only** writes it to disk too, and also makes it permanently read-only: it
> accepts no further documents.

That is genuinely good plain-language writing, already in this codebase, on another tab.

**Why it is third.** Carbon's rule for anything a user needs in order to finish the task:

> Use helper text that is always visible and accessible for vital information, such as
> required fields.

The two facts a user must know before pressing anything on this tab are vital, not
supplemental: that storage is fixed at creation (semantic-workbench.ts:609) and that
`Flat float (exact)` cannot be checkpointed (findings/gating-and-links.md section 3.4).
Those belong in helper text, not in a disclosure and certainly not in a tooltip.

**Cost here:** two `<small class="field-help">` elements. Zero new CSS.

### Rank 4: an information icon with a definition tooltip

**What it is.** Carbon has a dedicated component for exactly the owner's problem, the
**definition tooltip**:

> A definition tooltip is used to define terms or give extra help within text. It works
> well on UI labels, words in paragraphs, or compact spaces like data tables, where extra
> icons might clutter the interface.

Material Design 3's equivalent is the **rich tooltip**: "Rich tooltips are best used for
longer text like definitions or explanations", click-activated and persistent.

**Why it is only fourth.** Three reasons, all citable.

1. NN/g: "Don't use tooltips for information that is vital to task completion. ... Remember
   that tooltips disappear." The definition of "workspace" is vital on this tab; the whole
   tab is unusable without it.
2. The ARIA Authoring Practices are explicit that the icon pattern is not a tooltip:
   "The tooltip is not the appropriate role for the more information 'i' icon."
   (`reference/onboarding-inline-help-and-tooltips.md`, section 6.) An (i) icon that opens
   a panel is a *toggletip* or a disclosure, which loops back to rank 2.
3. Building an accessible one means implementing dismissible, hoverable, and persistent
   behaviour per WCAG 2.2 SC 1.4.13, in an app with no tooltip library. M3's own rich
   tooltip contract ("click activated and persistent") is functionally identical to a
   native disclosure, which is free.

**Where a tooltip IS right on this tab:** short supplemental glosses on jargon that is
nice to understand but not needed to act, such as `cosine` in the
`Similarity` fact (index.html:739) or `TurboQuant`. For those, prefer
`aria-describedby` pointing at a visually hidden sentence, which MDN explicitly sanctions:
"a form control can have a description that is hidden by default and revealed on request
using a disclosure widget". The `.visually-hidden` class already exists at style.css:1188.

### Rank 5: a contextual help panel

**What it is.** A side panel that opens over the page with fuller documentation. Stripe's
dashboard and Kibana both do this.

**Feasibility here is unusually good**, because the app already ships one:
`#annotation-details` is a `role="dialog" aria-modal="true"` drawer with a backdrop,
Escape-to-close, and focus return (index.html:369-370, annotation-drawer.ts:38-60). A
"help" variant would reuse that machinery.

**Why it is only fifth:** it is more surface than the problem needs. The concept fits in
three sentences. NN/g's rule 5, "Skip the obvious stuff. Save the contextual help for more
complex functionality or processes", argues for the smallest sufficient container. Revisit
this if a glossary of ten or more terms accumulates.

### Rank 6: coach marks, spotlights, and product tours

**What it is.** Atlassian: "A spotlight introduces users to points of interest, from
focused messages to multi-step tours."

**Why it is last.** NN/g, verbatim:

> Tutorials interrupt users, don't necessarily improve task performance, and are quickly
> forgotten.

and

> Showing multiple coach marks or tips in a row not only creates problems with users'
> short-term memory, but can also make your app appear overly complicated and daunting to
> new users.

It is also the single most expensive option to build without a framework: overlay
positioning, focus trapping, step state, a dismissal that persists across reloads. Note
that Atlassian's own older `onboarding` package is now marked **deprecated** in favour of
Spotlight, which is a sign of how much churn this pattern carries.

**Recommendation: do not build this.** P3 at best, and only after the free options are
exhausted.

## 3. The glossary-link pattern

Carbon's empty-state anatomy reserves a slot for it, verbatim:

> **Secondary call to action (optional):** If there is a secondary action, such as
> referencing documentation for further reading, include it as a link below the copy.

Polaris agrees from the other side: "Secondary actions are used for less important actions
such as 'Learn more' or 'Close' buttons."

FACT: this repository has **no glossary**. `find docs -iname '*glossar*'` returns nothing,
and the words "dynamic workspace" appear in exactly one non-UI place, README.md:752.

OPINION (P2): a short `docs/glossary.md` covering workspace, index, collection, alias,
chunk, embedding, vector space, cosine, seal, and checkpoint would pay for itself across
all seven tabs, not just this one. Every one of those terms is used in the UI today
without definition, and "collection" in particular means something different here from
what every vector database user will assume (findings/what-is-a-workspace.md section 3).

Precedent for a vendor-maintained glossary that the product links into: Elastic, Stripe,
and Atlassian all publish one. The pattern is: one page, one anchor per term, and the app
links to `#the-term`.

## 4. Recommendation

**P1, do all three of these together. Total cost: one HTML block, two `<small>` elements,
one replaced paragraph, no new CSS, no new JavaScript.**

1. **Rewrite the tab-bridge paragraph** (index.html:733-736) so the word is defined in its
   first six words. Exact text in findings/what-is-a-workspace.md section 5.1.
2. **Add a `<details class="help-callout">` titled "What is a workspace?"** directly under
   the heading, above the existing `How to use workspace search` callout. Exact markup in
   findings/what-is-a-workspace.md section 5.2. Carbon's rule "only one should open at a
   time" means it ships closed, like its six siblings.
3. **Rewrite the empty state** at index.html:784-786 into the Grafana four-part shape:
   title, definition sentence, one primary action linking to the Analyze tab, and later a
   glossary link. Exact text in findings/gating-and-links.md section 2.

**P2:**

4. Two `<small class="field-help">` elements: one under `#workspace-provider-select`
   saying storage is fixed at creation and that flat float cannot be checkpointed, one
   under the disabled `Add to server workspace` button on the Analyze tab carrying the
   currently unreachable string at semantic-workbench.ts:246.
5. A `docs/glossary.md`, linked from the disclosure's last line.

**P3:**

6. `aria-describedby` glosses on `cosine` and `TurboQuant`, using the existing
   `.visually-hidden` class.

**Do not build:** a tour, a coach mark, a hover tooltip library, or a modal that opens on
first load. The evidence in `reference/onboarding-inline-help-and-tooltips.md` is
consistent and one-directional on this point.

## 5. A note for a non-front-end reader

The reason the recommendation is so cheap is that the browser already implements the two
patterns that matter. `<details><summary>` is a native disclosure widget: the browser
gives it keyboard support, the correct accessibility role, and open and closed states with
no script at all. MDN, quoted in `reference/onboarding-inline-help-and-tooltips.md`:

> When the user clicks on the widget or focuses it then presses the space bar, it "twists"
> open, revealing its contents.

And an empty state is just a paragraph and a button. Everything expensive on the list
above (tooltips, coach marks, tours) is expensive precisely because it fights the browser
instead of using it.

## Questions for the lead

1. Is a `docs/glossary.md` in scope for this pass, or should the disclosure carry the
   definitions inline until one exists?
2. Should the "What is a workspace?" disclosure be duplicated on the Lifecycle tab, which
   uses the same word (index.html:1003, `Dynamic workspace`), or should it live in one
   place and be linked?
3. Carbon's rule is one primary action per empty state. The empty state here has two
   plausible ones: "Analyze a document" and "Pick an existing workspace". Confirm that
   "Analyze a document" is the one to keep.
