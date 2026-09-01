# Design system guidance on teaching an unfamiliar concept

Fetched: 2026-08-28

Sources:
- IBM Carbon Design System, Empty states pattern: https://carbondesignsystem.com/patterns/empty-states-pattern/
- IBM Carbon Design System, Tooltip usage: https://carbondesignsystem.com/components/tooltip/usage/
- IBM Carbon Design System, Disclosures pattern: https://carbondesignsystem.com/patterns/disclosures-pattern/
- Material Design 3, Tooltips guidelines: https://m3.material.io/components/tooltips/guidelines
- Atlassian Design System component pages: https://atlassian.design/components/empty-state , https://atlassian.design/components/inline-message , https://atlassian.design/components/spotlight , https://atlassian.design/components/section-message , https://atlassian.design/components/tooltip
- Shopify Polaris, Empty state component: https://polaris.shopify.com/components/layout-and-structure/empty-state

Verbatim source text for the Carbon pages was read from the published docs repository
(https://github.com/carbon-design-system/carbon-website, `src/pages/patterns/` and `src/pages/components/`),
which is the same content the website renders.

---

## 1. Carbon: the single most on-point rule for our problem

Carbon's "no data empty states" section lists this under **Don't**:

> Don't use product-specific terms that the user may not yet understand.

and, in the same Don't list:

> Don't cover multiple options in one empty state. If there are multiple things a user can do,
> pick the most important and keep the focus on that action.

Source: https://carbondesignsystem.com/patterns/empty-states-pattern/

This is the direct precedent for our situation. A term such as "workspace index" is a
product-specific term. Carbon's guidance is not "add a tooltip", it is "do not lean on the term".
The concept has to be explained in the space itself, in plain words, before it is named.

## 2. Carbon: "in-line documentation" is a named, sanctioned pattern

Carbon treats an expanded, explanatory empty state as its own approach, under
"In-depth alternatives":

> In-line documentation is an extension of the basic empty state for first time use. It can be most
> helpful when a primary feature is first introduced, providing more detail and highlighting any
> benefits. Including an image of the space populated with data may help trigger interest and usage.
> Following a progressive disclosure model, it could provide links out to more detailed documentation.

Considerations Carbon attaches to it:

> If testing results show that users do not understand the feature or concept, more detail may
> encourage usage.

> Keep the content limited to one feature. Do not talk about other areas of the app. If there are
> multiple things a user could do, pick the most important and keep the focus on that.

Carbon also sets the threshold for when to spend this effort:

> a good rule of thumb is that a primary resource on a page could benefit from a more educational
> approach, while basic empty states may suffice for secondary resources.

**When to use:** the feature is primary, and testing or support traffic shows the concept itself is
not understood.
**When NOT to use:** secondary features, or where a one line empty state already unblocks the user.
Carbon warns that "More content doesn't necessarily mean it's a better solution as there is a
cognitive cost for having more content on the page."

## 3. Carbon: definition tooltip

Carbon keeps one tooltip variant as a standalone component specifically for explaining terms:

> A definition tooltip is used to define terms or give extra help within text. It works well on UI
> labels, words in paragraphs, or compact spaces like data tables, where extra icons might clutter
> the interface.

Content and interaction rules Carbon gives it:

> The primary purpose of a Definition Tooltip is to provide additional help or define an item or
> term. Therefore, they should contain read-only text that is kept to a minimum.

> For definitions and instructive tooltips, use sentence-style capitalization and write the text as
> complete sentences with punctuation unless space is limited.

> Definition tooltips can use either hover or click interactions, depending on the situation. Users
> can hover if they need a quick glance at the information. Users can click if they need more time
> or if the tooltip might be unintentionally triggered.

Trigger styling, from the anatomy list: the UI trigger is "any component with integrated tooltips or
definition terms with dotted underlines".

**When NOT to use** (Carbon, "When not to use"):

> Since a tooltip disappears when a user hovers away, do not include pertinent information for the
> user to complete their task. Use helper text that is always visible and accessible for vital
> information, such as required fields.

> Do not include interactive elements within a tooltip. Interactive elements in tooltips are
> inaccessible for some users and are hard to use for all users since tooltips do not receive focus.

Carbon's separation of the two is explicit:

> A tooltip is exposed on hover or focus when you need to disclose brief, supplemental information
> that is not interactive. A toggletip is used on click or enter when you must expose interactive
> elements, such as a button, that a user needs to interact with.

Implication for us: a definition popup that contains a "Learn more" link is not a tooltip. If it
holds a link, it is a disclosure or toggletip and must be click activated and keyboard reachable.

## 4. Carbon: disclosures pattern (the closest match to native details/summary)

> Disclosures are moments that open up on a page and reveal additional information related to the
> source it is triggered from.

> Unlike tooltips, the content expanded by a disclosure may contain interactive elements.

> At its core, a disclosure is comprised of two parts, a trigger that the user interacts with by
> clicking or using their keyboard and the container that opens and discloses the content.

Best practices, quoted:

> Disclosures should always be triggered to open or close by the user. Disclosures should never open
> automatically because this could be potentially intrusive to the user's workflow.

> If there are multiple instances on a page where a disclosure is present, only one should open at a
> time to avoid screen clutter and to help the user stay focused on the information being disclosed.

> Do not nest one disclosure within another disclosure.

> Do not hide important information inside of a disclosure that the user may need in order to
> complete a task or workflow. Keep critical information at a higher level so the user has better
> visibility.

Source: https://carbondesignsystem.com/patterns/disclosures-pattern/

## 5. Material Design 3: plain tooltip vs rich tooltip

> A tooltip provides additional context for a UI element.

> Plain tooltips briefly describe a UI element. They're best used for labelling UI elements with no
> text, like icon-only buttons and fields.

> Rich tooltips provide additional context about a UI element. They can optionally contain a
> subhead, buttons, and hyperlinks.

> Rich tooltips are best used for longer text like definitions or explanations.

Do and Don't captions on the same page:

> Do: Use rich tooltips to provide extra information and actions about a UI element or new feature

> Don't: Don't hide critical information within tooltips as it's easy to miss. Use an interruptive
> dialog instead.

> Don't: Plain tooltips aren't needed when the UI element already has label text

Behaviour rules that matter if we ever build one:

> Both plain and rich tooltips disappear 1.5 seconds after navigating away from the target region.

> Persistent rich tooltips appear when either: The parent element is clicked; The page loads and a
> new feature is being explained.

> Persistent rich tooltips remain active even when leaving the target region. They only disappear
> once a person interacts with another UI element. Hovering doesn't trigger the tooltip.

> Subheads are important to include when the rich tooltip appears automatically, like when the page
> loads.

> Only display one tooltip at a time

Source: https://m3.material.io/components/tooltips/guidelines

**Read across:** M3's "rich tooltip" is the only mainstream tooltip variant that is allowed to
contain a definition plus a link. It is click activated and persistent. That is functionally the
same contract as a native disclosure, which we already have for free.

## 6. Atlassian Design System: the vocabulary, verbatim

Each of these is the component's own one line definition as published on atlassian.design:

- Empty state: "An empty state appears when there is no data to display and describes what the user can do next."
- Inline message: "An inline message lets users know when important information is available or when an action is required."
- Section message: "A section message is used to alert users to a particular section of the screen."
- Spotlight: "A spotlight introduces users to points of interest, from focused messages to multi-step tours."
- Tooltip: "A tooltip briefly describes an interactive element on mouse hover or keyboard focus."
- Banner: "A banner displays a prominent message at the top of the screen."

Atlassian's older onboarding component page read: "An onboarding spotlight introduces new features to
users through focused messages or multi-step tours." That package is now marked **deprecated** in
favour of Spotlight (https://atlassian.design/components/onboarding).

Note for us: Atlassian's tooltip definition is scoped to "briefly describes an interactive element".
It is not a definition mechanism. Atlassian's teaching mechanism is Spotlight, which is a tour
overlay and is the heaviest thing on this list.

## 7. Shopify Polaris

Polaris empty state guidance is quoted in `onboarding-empty-states.md` in this directory.
Source: https://polaris.shopify.com/components/layout-and-structure/empty-state
