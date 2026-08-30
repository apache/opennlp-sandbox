# Inline help, disclosure, tooltips, and what is accessible without a framework

Fetched: 2026-08-28

Sources:
- NN/g, "Onboarding Tutorials vs. Contextual Help": https://www.nngroup.com/articles/onboarding-tutorials/
- NN/g, "Progressive Disclosure": https://www.nngroup.com/articles/progressive-disclosure/
- NN/g, "Tooltip Guidelines": https://www.nngroup.com/articles/tooltip-guidelines/
- NN/g, "Instructional Overlays and Coach Marks for Mobile Apps": https://www.nngroup.com/articles/mobile-instructional-overlay/
- MDN, `<details>`: https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/details
- W3C WAI-ARIA Authoring Practices, Disclosure (Show/Hide) pattern: https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/
- MDN, ARIA `tooltip` role: https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/tooltip_role
- MDN, `aria-describedby`: https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Attributes/aria-describedby
- W3C, Understanding SC 1.4.13 Content on Hover or Focus: https://www.w3.org/WAI/WCAG22/Understanding/content-on-hover-or-focus.html

---

## 1. NN/g: prefer pull over push. This is the headline finding

Summary line of the article:

> Tutorials interrupt users, don't necessarily improve task performance, and are quickly forgotten.
> Contextual help signals can avoid these pitfalls but require unintrusive ways to activate.

> Pull revelations are help content triggered by some signal that the user would benefit from that
> information at that moment. Pull revelations can come in a variety of formats ranging from hover
> tooltips or coach marks to more expansive patterns like step-by-step task-flow wizards.

The guidelines, verbatim:

> 1. Make it easy to dismiss (and recall) the help content. Being able to get rid of a (momentarily)
>    unhelpful overlay is absolutely critical, but so is the ability to find this information again
>    later, when it is actually useful.
> 2. Use progressive disclosure in the help content. Make the existence of the contextual help
>    visible, but do not overwhelm the user with detail until they ask for it.
> 3. No memorization! ... show help content alongside each step.
> 4. Skip the obvious stuff. ... Save the contextual help for more complex functionality or processes.
> 5. Understand the user's journey to that feature.

Guideline 2 is a direct endorsement of a collapsed-by-default explainer that is visible but not
expanded. Guideline 1 says the explainer must remain reachable after dismissal, which rules out a
one-shot tour as the only place the concept is explained.

Source: https://www.nngroup.com/articles/onboarding-tutorials/

## 2. NN/g: progressive disclosure

> Initially, show users only a few of the most important options. Offer a larger set of specialized
> options upon request.

Rationale: it improves "3 of usability's 5 components: learnability, efficiency of use, and error
rate."

Two things you must get right: "get the right split between initial and secondary features", and make
it "obvious how users progress" to the second level. On labelling the trigger, label it "in a way
that sets clear expectations for what users will find when they progress to the next level."

Practical read: the `<summary>` text is the progression label. "What is a workspace index?" sets a
clear expectation. "More info" does not.

Source: https://www.nngroup.com/articles/progressive-disclosure/

## 3. NN/g: tooltips, and why they are the wrong home for a definition users need

> A tooltip is a brief, informative message that appears when a user interacts with an element in a
> graphical user interface (GUI).

> One important aspect of tooltips is that they are user-triggered. Therefore, tips that pop up on
> pages to inform users about new features ... are not tooltips.

Guideline 1, verbatim heading and body:

> Don't use tooltips for information that is vital to task completion.
> Users shouldn't need to find a tooltip in order to complete their task. ... Remember that tooltips
> disappear, so instructions or other directly actionable information, like field requirements,
> shouldn't be in a tooltip.

Other guidelines, verbatim headings: "Provide brief and helpful content inside the tooltip",
"Support both mouse and keyboard hover", "Use tooltip arrows when multiple elements are nearby",
"Use tooltips consistently throughout your site." On discoverability: "Tooltips are hard to discover
because they often lack visual cues."

Closing test:

> The next time you consider a tooltip, ask: is the information in the tooltip necessary for users in
> order to complete a task? If the answer is no, a tooltip is well-suited. Otherwise, the information
> should be present on the screen.

By that test, the definition of "workspace index" fails the tooltip test. A user cannot use the tab
without it, so it belongs on screen.

Source: https://www.nngroup.com/articles/tooltip-guidelines/

## 4. NN/g: coach marks and first-run overlays

Quoted at length in `onboarding-product-examples.md` in this directory, alongside the products that
ship them. The short version:

> Showing multiple coach marks or tips in a row not only creates problems with users' short-term
> memory, but can also make your app appear overly complicated and daunting to new users.

**When to use:** one unfamiliar interaction, shown at the moment it becomes relevant.
**When NOT to use:** to define a noun. A coach mark explains a gesture or a control, not a concept,
and it is gone on the second visit.

Source: https://www.nngroup.com/articles/mobile-instructional-overlay/

## 5. Native `<details>` / `<summary>`: what you get for free

MDN:

> A `<details>` widget can be in one of two states. The default closed state displays only the
> triangle and the label inside `<summary>` (or a user agent-defined default string if no
> `<summary>`).

> When the user clicks on the widget or focuses it then presses the space bar, it "twists" open,
> revealing its contents.

Attributes worth knowing. `open` is "a Boolean attribute" and "You have to remove this attribute
entirely to make the details hidden." `name` "enables multiple `<details>` elements to be connected,
with only one open at a time. This allows developers to easily create UI features such as accordions
without scripting", which is the zero-JavaScript way to satisfy Carbon's one-open-at-a-time rule.

Events: the element "supports the `toggle` event, which is dispatched to the `<details>` element
whenever its state changes between open and closed." That is the hook if we want to record that a
user opened the explainer.

The WAI-ARIA Authoring Practices disclosure pattern describes the same widget:

> A disclosure is a widget that enables content to be either collapsed (hidden) or expanded
> (visible). It has two elements: a disclosure button and a section of content whose visibility is
> controlled by the button.

Required behaviour, which browsers already implement for `<details>`: Enter and Space each
"activates the disclosure control and toggles the visibility of the disclosure content"; the trigger
"has role button" and carries `aria-expanded` true when open, false when closed.

Practical note: browsers expose `<summary>` with the button role and manage `aria-expanded`
themselves, so do not hand author either on a `<summary>`. Do give the `<details>` a stable `id` if
anything else needs to link to it.

## 6. Accessible tooltips, if we build one anyway

MDN on the `tooltip` role:

> Because the tooltip itself never receives focus and is not in the tabbing order, a tooltip can not
> contain interactive elements like links, inputs, or buttons.

> The tooltip is not the appropriate role for the more information "i" icon. A tooltip is directly
> associated with the owning element.

> An example of a native browser tooltip is the way some browsers display an element's `title`
> attribute on long mouse hover. One cannot activate this feature through either keyboard focus or
> through touch interaction, making this feature inaccessible. If the information is important
> enough to include as a tooltip or title, consider including it in visible text.

> Elements with the `tooltip` role should be referenced through the use of `aria-describedby` before
> or when the tooltip is displayed. The `aria-describedby` attribute is on the owning element, not on
> the tooltip.

Required behaviour: appears on focus and on hover with no extra interaction, closes on Escape, stays
open while hovered, and never takes focus.

MDN on `aria-describedby`, which is the useful part even without a tooltip:

> The elements linked via `aria-describedby` don't need to be visible. It is possible to reference an
> element even if that element is hidden. For example, a form control can have a description that is
> hidden by default and revealed on request using a disclosure widget like a "more information" icon.

> The `aria-describedby` property is appropriate when the associated content contains plain text. If
> the content is extensive, contains useful semantics, or has a complex structure requiring user
> navigation, use `aria-details` instead.

So a one sentence definition can be attached to a control with `aria-describedby`. A multi paragraph
explainer with links should not be, and should be a real disclosure instead.

## 7. WCAG 2.2 SC 1.4.13, the hard constraint on anything that appears on hover

> Where receiving and then removing pointer hover or keyboard focus triggers additional content to
> become visible and then hidden, the following are true:
> Dismissible: A mechanism is available to dismiss the additional content without moving pointer
> hover or keyboard focus, unless the additional content communicates an input error or does not
> obscure or replace other content;
> Hoverable: If pointer hover can trigger the additional content, then the pointer can be moved over
> the additional content without the additional content disappearing;
> Persistent: The additional content remains visible until the hover or focus trigger is removed, the
> user dismisses it, or its information is no longer valid.

> Custom tooltips, sub-menus, and other nonmodal popups that display on hover and focus are examples
> of additional content covered by this criterion.

And the escape hatch we should take:

> There are usually more predictable and accessible means of adding content to the page, which
> authors are recommended to employ.

A native disclosure is not covered by this criterion at all, because it is click activated rather
than hover activated. Choosing `<details>` removes three separate conformance obligations.
