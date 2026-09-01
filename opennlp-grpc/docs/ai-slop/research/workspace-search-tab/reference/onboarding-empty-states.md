# The empty state as the place where a new concept gets taught

Fetched: 2026-08-28

Sources:
- Nielsen Norman Group, "Designing Empty States in Complex Applications: 3 Guidelines": https://www.nngroup.com/articles/empty-state-interface-design/
- IBM Carbon Design System, Empty states pattern: https://carbondesignsystem.com/patterns/empty-states-pattern/
- Shopify Polaris, Empty state component: https://polaris.shopify.com/components/layout-and-structure/empty-state
- Atlassian Design System, Empty state: https://atlassian.design/components/empty-state
- Grafana in product strings, `public/locales/en-US/grafana.json`: https://github.com/grafana/grafana/blob/main/public/locales/en-US/grafana.json

---

## What it is

Nielsen Norman Group's definition:

> At times, users will encounter empty states within an application: containers, screens, or panels
> for which content does not yet exist or otherwise cannot be displayed.

Carbon's definition:

> Empty states are moments in an app where there is no data to display to the user. They are most
> commonly seen the first time a user interacts with a product or page, but can be used when data
> has been deleted or is unavailable.

Atlassian's one line definition:

> An empty state appears when there is no data to display and describes what the user can do next.

## Why this is the right surface for teaching a concept

NN/g's three guideline headings are, verbatim:

1. "Use Empty States to Communicate System Status"
2. "Use Empty States to Provide Learning Cues"
3. "Use Empty States to Provide Direct Pathways for Key Tasks"

and:

> Empty states present an opportunity to provide contextual help relevant to the user's task

> can help increase user confidence, improve system learnability, and help users get started with
> key tasks

The failure mode NN/g names:

> Do not default to totally empty states. This approach creates confusion for users, who may be left
> wondering if the system is still loading information or if errors have occurred.

Carbon frames the same point:

> They provide opportunities to communicate what the user would see if they had data, while
> providing constructive guidance about next steps.

## Anatomy (Carbon, verbatim, lightly trimmed)

1. **Image (optional):** "A non-interactive image that relates to the situation (optional)."
2. **Title:** "A short and concise explanation. Where possible, write this as a positive statement."
   Carbon's own example: "Start by adding data assets" reads more positively than
   "You don't have any data assets."
3. **Body:** "Explain clearly the next action to populate the space. You may also explain why the
   space is empty and include the benefit of taking this step." Carbon gives three options for the
   primary action: a primary action button under the copy, a primary action link inside the copy, or
   pointing the user at the actual UI element, which "has the benefit of teaching the user where
   elements are and how they will perform tasks in the future."
4. **Primary action (optional):** button or link in copy.
5. **Secondary call to action (optional):** "If there is a secondary action, such as referencing
   documentation for further reading, include it as a link below the copy."

Point 5 is the sanctioned home for a "Learn more" or glossary link. Polaris says the same thing from
the other direction:

> Secondary actions are used for less important actions such as "Learn more" or "Close" buttons.

## When to use which flavour (Carbon's table, condensed)

| Type | Use case | When to use |
| --- | --- | --- |
| No data | First time use, no data yet | "For simpler situations, or for secondary features where bite-sized pieces of information are preferable." |
| User action | No results when searching, confirmation of a completed process | "When you need to provide feedback to the user based on an interaction." |
| Error management | Permissions issue, systems issue, configuration required | "When something is amiss or some level of intervention or troubleshooting is required, a higher level of detail and specificity will better support the user." |

Carbon on sizing the content:

> Strive for a balance between the situation and the content you're providing. More content doesn't
> necessarily mean it's a better solution as there is a cognitive cost for having more content on
> the page. This is especially true when users first engage with your product, so save the more
> involved educational moments for primary features and more complex situations.

## Do (Carbon, no data empty states)

> - Use basic empty states for simpler situations, or secondary features, where bite-sized pieces of
>   information are preferable.
> - Be specific about what will be available in the space when data is there.
> - Keep words to a minimum so they are fast to read and act upon.
> - If there is an actionable next step, include a direct link in your message copy or a primary
>   action button to make that action fast. Alternatively, guide them to what they need to click.

## Don't (Carbon, no data empty states)

> - Don't cover multiple options in one empty state. If there are multiple things a user can do,
>   pick the most important and keep the focus on that action.
> - Don't use product-specific terms that the user may not yet understand.

From the error management list:

> - Use direct, plain language to describe the situation.
> - Be respectful of the user and don't joke or use flippant language.
> - Don't include content that is about other areas of the app. Be contextual.

## Polaris best practices, verbatim

> Use to explain a single feature before merchants have used it.

> Orient merchants by clearly explaining the benefit and utility of a product or feature

> Simplify a complicated experience by focusing on a few key features and benefits

> Use simple and clear language that empowers merchants to move their business forward

> Be encouraging and never make merchants feel unsuccessful or guilty because they haven't used a
> product or feature

> Explain the steps merchants need to take to activate a product or feature

> Use only one primary call-to-action button

Titles: "Be action-oriented: encourage merchants to take the step required to activate the product or
feature." Subtitles: "Describe or explain what's in the empty state title or item title" and
"Be conversational: include articles such as the, a, and an."

Polaris also scopes the component: "The empty state component is intended for use when a full page in
the admin is empty, and not for individual elements or areas in the interface."

## A shipped example of exactly the shape we want

Grafana's annotations empty state is a four part block: a title, a paragraph that defines the concept
from scratch, a docs link, and one primary button. From
`public/locales/en-US/grafana.json` in the Grafana repository:

- `annotations.empty-state.title`: "There are no custom annotation queries added yet"
- `annotations.empty-state.info-box-content`: "Annotations provide a way to integrate event data into
  your graphs. They are visualized as vertical lines and icons on all graph panels. When you hover
  over an annotation icon you can get event text & tags for the event. You can add annotation events
  directly from grafana by holding CTRL or CMD + click on graph (or drag region). These will be
  stored in Grafana's annotation database."
- `annotations.empty-state.info-box-content-2`: "Checkout the <2>Annotations documentation</2> for
  more information."
- `annotations.empty-state.button-title`: "Add annotation query"

The same four part shape appears again for Alertmanager import:

- `alerting.settings.import.empty-title`: "No configuration imported yet"
- `alerting.settings.import.empty-body`: "Import an Alertmanager configuration to stage it here as a
  safe, reversible copy. Review what it contains, then promote it into your live Grafana
  Alertmanager..."
- `alerting.settings.import.empty-cta`: "Import Alertmanager configuration"
- `alerting.settings.import.empty-learn-more`: "Learn more about importing configurations"

And the plain, no teaching variant, for a concept users already know:

- `browse-dashboards.empty-state.title`: "You haven't created any dashboards yet"
- `browse-dashboards.empty-state.button-title`: "Create dashboard"
- `dashboard.empty.add-visualization-header`: "Start your new dashboard by adding a visualization"
- `dashboard.empty.add-visualization-body`: "Select a data source and then query and visualize your
  data with charts, stats and tables or create lists, markdowns and other widgets."

Note the deliberate difference. "Dashboard" needs no definition, so its empty state is a title plus a
button. "Annotation" does need one, so its empty state carries a definition paragraph and a docs
link. That is the decision we are making about "workspace index".

## Accessibility

Carbon:

> As most empty state illustrations are considered decorative, they should be skipped by screen
> readers.

> A decorative image is one that does not serve any practical or informational purpose, and is
> included to fill a visual void. Do not include any informational content in your decorative image.

> Web Content Accessibility Guidelines require that decorative images are given either an empty `alt`
> tag or their `role` is assigned `presentation`. As an empty `alt` tag is more widely supported, we
> recommend you align with the WCAG guidance and avoid assigning `role` to `presentation` until
> support is more ubiquitous.

Polaris says the same: "Empty state illustrations are implemented as decorative images, so they use
an empty alt attribute and are skipped by technologies like screen readers."

Practical rule for a plain HTML app: the empty state is ordinary flow content. Give it a real heading
element so it lands in the document outline, keep the explanatory paragraph as visible text rather
than a hover target, and mark any decorative glyph `alt=""` or `aria-hidden="true"`.
