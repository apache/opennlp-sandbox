# The glossary page, and how an app links into it

Fetched: 2026-08-28

Sources:
- Elastic Docs glossary: https://www.elastic.co/docs/reference/glossary
- Stripe glossary: https://docs.stripe.com/glossary
- Atlassian agile glossary: https://www.atlassian.com/agile/glossary
- IBM Carbon, definition tooltip guidance: https://carbondesignsystem.com/components/tooltip/usage/
- NN/g, progressive disclosure: https://www.nngroup.com/articles/progressive-disclosure/

---

## What the pattern is

A single canonical page that lists every product-specific term with a short, self-contained
definition, where each term has a stable anchor so any surface in the product can deep link to the
exact entry.

The three examined here all take the same shape: one flat A to Z page, term as the heading, one or
two sentences of definition, optional "see also" link into the deeper docs.

## Elastic: the reference implementation, because the anchors are stable and predictable

Elastic publishes one glossary covering Elasticsearch, Kibana, and Elastic Cloud terminology at
https://www.elastic.co/docs/reference/glossary

Every entry carries an id of the form `glossary-<slug>`. Verified live on the fetch date:

- `#glossary-index`
- `#glossary-data-view`
- `#glossary-data-stream`
- `#glossary-alias`
- `#glossary-analysis`

So the app can link a term straight to its definition:
https://www.elastic.co/docs/reference/glossary#glossary-data-view

Entries are one sentence plus a pointer. Examples of the house style, quoted from the glossary and
the data views page it points at:

> A data view can point to one or more data streams, indices, or aliases.

> Data stream or index excluded from most index patterns by default. See Hidden data streams and
> indices.

> Index pattern that automatically configures new indices as follower indices for cross-cluster
> replication. See Manage auto-follow patterns.

Note the structure: definition sentence, then "See <deeper page>". The glossary never tries to be the
tutorial.

**Directly relevant to us:** Elastic has to define "index", "index pattern", "data view", and "data
stream" as four separate glossary entries because users confuse them. We have the same problem with
"index" versus "workspace index".

## Stripe: glossary as a single scannable page, term run into the definition

https://docs.stripe.com/glossary is one long A to Z list. The rendered shape is the term immediately
followed by its definition on the same line. Verbatim entries:

> account ID  When you create a Stripe account, Stripe generates a unique account ID for you. Find
> your account ID in the Dashboard by navigating to Profile > Accounts.

> application fee  A fee that Connect platforms collect from connected accounts for each payment they
> receive.

> asynchronous  Asynchronous refers to events happening at independent times in independent systems.

House style, worth copying:

1. One sentence. Two at most, where the second says where to find the thing.
2. Written for someone who has never seen the product. No forward references to other Stripe nouns
   unless those nouns are also in the glossary.
3. Sense-disambiguated entries where a term genuinely has two meanings, for example
   "advanced risk factors (Radar Session disabled)" and "advanced risk factors (Radar Session
   enabled)" are two separate entries rather than one hedged definition.

## Atlassian: glossary as marketing-adjacent education

https://www.atlassian.com/agile/glossary is the same flat list, but it sits on the marketing site
rather than in product docs, and its entries link out to full articles. That placement is a
deliberate choice: the terms it defines are industry terms, not product nouns, so the page doubles as
content marketing.

**Contrast to draw:** a product noun such as "workspace index" belongs in the product docs glossary
next to the feature, not in a general vocabulary page. Elastic and Stripe are the right models here,
Atlassian is not.

## How the app links into the glossary

Three linking styles observed, in increasing weight:

1. **A plain "Learn more" link in the empty state.** Polaris explicitly names this as the secondary
   action slot: "Secondary actions are used for less important actions such as 'Learn more' or
   'Close' buttons." Carbon calls it the secondary call to action: "If there is a secondary action,
   such as referencing documentation for further reading, include it as a link below the copy."
2. **A definition tooltip on the term itself.** Carbon: "A definition tooltip is used to define terms
   or give extra help within text. It works well on UI labels, words in paragraphs, or compact spaces
   like data tables, where extra icons might clutter the interface." Carbon renders the trigger as a
   dotted underline on the word. Constraint: Carbon forbids interactive content inside a tooltip, so
   a definition tooltip cannot itself contain the glossary link. It can only hold the sentence.
3. **An inline disclosure that contains both the definition and the link.** This is the only one of
   the three that can hold prose plus a link plus keyboard access with no framework. See
   `onboarding-inline-help-and-tooltips.md` in this directory.

## When to use a glossary link

- The term appears in more than one place in the product, so the definition should not be duplicated.
- The definition is longer than one sentence, or the user may want the deeper explanation later.
- Someone needs to be able to send the definition to a colleague as a URL.

## When NOT to use a glossary link as the primary teaching mechanism

- When the user is blocked right now. NN/g: "is the information in the tooltip necessary for users in
  order to complete a task? If the answer is no, a tooltip is well-suited. Otherwise, the information
  should be present on the screen." The same test applies to an offsite link. A link is a second
  click and a context switch.
- As the only explanation. Carbon's rule against product-specific terms the user does not yet
  understand is not satisfied by linking the term. The empty state still has to say what the thing
  is, in one sentence, on screen.
- Progressive disclosure requires the trigger label to set expectations: label the link with what the
  reader will find, not "Learn more" in isolation. NN/g says to label the progression "in a way that
  sets clear expectations for what users will find when they progress to the next level."

## Accessibility notes

- A glossary link is an ordinary anchor. It needs link text that makes sense read out of context, so
  "What is a workspace index?" rather than "Learn more" or "click here".
- If the link leaves the app, say so in the link text or with a visible external-link marker plus an
  accessible name. Do not rely on `title` alone. MDN on the `title` attribute as a tooltip source:
  "One cannot activate this feature through either keyboard focus or through touch interaction,
  making this feature inaccessible."
- Deep links to a glossary anchor should land on a heading so screen reader users hear the term when
  focus moves. Elastic's `glossary-<slug>` ids sit on the term heading, which is why the pattern works.
- If a definition tooltip is used on the term, the trigger must be a focusable element, and per
  WCAG 2.2 SC 1.4.13 the revealed content must be dismissible, hoverable, and persistent. A
  `<details>` disclosure sidesteps that criterion entirely because it is click activated.
