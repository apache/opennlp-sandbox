# Concrete product precedent for teaching a product-specific noun

Fetched: 2026-08-28

Sources:
- GitHub Docs, "What are GitHub Codespaces?": https://docs.github.com/en/codespaces/about-codespaces/what-are-codespaces
- GitHub Docs, "Quickstart for repositories": https://docs.github.com/en/repositories/creating-and-managing-repositories/quickstart-for-repositories
- Notion Help, "Intro to databases in Notion": https://www.notion.com/help/intro-to-databases
- Elastic Docs, "Data views": https://www.elastic.co/docs/explore-analyze/find-and-organize/data-views
- Elastic Docs, "Ingest: Bring your data to Elastic": https://www.elastic.co/docs/manage-data/ingest
- Grafana docs, dashboards overview: https://grafana.com/docs/grafana/latest/fundamentals/dashboards-overview/
- Grafana in product strings: https://github.com/grafana/grafana/blob/main/public/locales/en-US/grafana.json
- Linear Docs, Start Guide: https://linear.app/docs/start-guide
- Stripe Docs, Testing use cases: https://docs.stripe.com/test-mode
- NN/g, coach marks: https://www.nngroup.com/articles/mobile-instructional-overlay/

---

## GitHub: the "What is X?" doc page, named after the question

GitHub does not explain a codespace inside a tooltip. It owns a documentation page whose title is
literally the user's question, and whose first sentence is a one line definition:

> A codespace is a development environment that's hosted in the cloud.

Page title: "What are GitHub Codespaces?". Page subtitle: "Learn about what GitHub Codespaces are."
URL: https://docs.github.com/en/codespaces/about-codespaces/what-are-codespaces

Sibling pattern for the empty repository. GitHub's quickstart is scoped by time:

> Learn how to create a new repository and commit your first change in 5 minutes.

URL: https://docs.github.com/en/repositories/creating-and-managing-repositories/quickstart-for-repositories

**Takeaway:** the definition lives in one canonical, linkable place with a question-shaped title, and
the product links into it. Nothing in the UI has to carry the full explanation.

## Notion: the help doc leads with a one sentence definition

The "Intro to databases in Notion" page opens with:

> Databases in Notion are collections of pages. Here, we'll introduce you to the general structure of
> a database, walk you through the different menus and options, and deep dive into how to open and
> edit pages within a database.

> Databases are one of Notion's fundamental features. They help you manage and organize multiple
> pages in one place.

It then explains what makes the concept unusual before explaining how to create one, and the "create"
instructions name the exact UI affordance:

> To create a database in Notion, create a new page and under `Get started with`, select `Table`. You
> can also open an existing page and use the slash command `/database`.

URL: https://www.notion.com/help/intro-to-databases

**Takeaway:** definition first, then what makes it different from what the user already knows, then
the click path. Three moves, in that order.

## Kibana and Elastic: define the prerequisite in the sentence that demands it

Elastic's data views page opens by tying the concept to the thing the user is trying to do:

> By default, analytics features such as Discover require a data view to access the Elasticsearch
> data that you want to explore. A data view can point to one or more indices, data streams, or index
> aliases. For example, a data view can point to your log data from yesterday, or all indices that
> contain your data.

It also tells the user when the concept will already be handled for them, which is a good way to keep
first run friction low:

> If you collected data using one of the Kibana ingest options, uploaded a file, or added sample
> data, you get a data view created automatically, and can start exploring your data. If you loaded
> your own data, follow these steps to create a data view.

And it explains the empty or blocked state cause up front:

> If a read-only indicator appears, you have insufficient privileges to create or save data views. In
> addition, the buttons to create data views or save existing data views are not visible.

URL: https://www.elastic.co/docs/explore-analyze/find-and-organize/data-views

**Takeaway:** the definition is a subordinate clause of the task sentence, not a standalone lecture.
"X requires a Y to do Z. A Y is ..." That single structure is directly transferable.

## Grafana: two different empty states, deliberately

Grafana's own translation catalogue shows the decision we need to make, made twice with different
answers. See `onboarding-empty-states.md` in this directory for the full strings.

- "Dashboard" is assumed known. Its empty state is a heading plus a button:
  "You haven't created any dashboards yet" and "Create dashboard".
- "Annotation" is not assumed known. Its empty state carries a full definition paragraph plus a docs
  link plus one button: "There are no custom annotation queries added yet", then "Annotations provide
  a way to integrate event data into your graphs...", then "Checkout the Annotations documentation
  for more information.", then "Add annotation query".

Grafana's docs also define the primary noun in one sentence:

> A panel is a container that displays the visualization and provides you with various controls to
> manipulate it.

URL: https://grafana.com/docs/grafana/latest/fundamentals/dashboards-overview/

## Linear: a start guide plus an explorable demo, not a forced tour

Linear's Start Guide opens with a plain statement of what the product is for, then offers three entry
points before any setup:

> Linear helps teams plan, track, and deliver work without a lot of overhead. This guide gets you
> from new workspace to working in Linear quickly.

> Start here if you want to understand the layout and core workflows before setting anything up.

The three are a walkthrough video, a live demo workspace, and a live onboarding session. The demo is
described as:

> Explore our demo workspace to see how issues, projects, and workflows fit together.

> Note: Changes are local to your browser and reset on refresh.

URL: https://linear.app/docs/start-guide

**Takeaway:** the sandbox with throwaway state is a real alternative to explanation. Carbon calls this
"starter content" and says it lets users "tinker, examining and deleting content without serious
consequences".

## Stripe: the mode banner and the notification box

Stripe's dashboard signals a non-obvious environment concept with persistent chrome rather than a
tooltip. From the docs describing that behaviour:

> Many Dashboard pages have a notification box and disable live mode settings while in the test mode
> sandbox. In this case, any settings still enabled are safe to use. If you don't see the
> notification, assume any changes made in the test mode sandbox affect live mode settings (unless
> you see a test data banner).

The concept itself is defined in one sentence at the top of the docs page:

> Sandboxes are the Stripe testing environment. They allow you to test your integration without
> making actual charges or payments.

URL: https://docs.stripe.com/test-mode

**Takeaway:** when the concept changes what an action means, the explanation is a persistent banner,
not a hover target. Persistent, always visible, and it names the consequence.

## Coach marks and first-run overlays (Colab, Figma, mobile apps)

The generic pattern is a transparent overlay of hints on first launch. NN/g's assessment:

> Almost every app on the market today has some sort of coach mark (a transparent overlay of UI
> hints) or tutorial shown on the first launch. While the presence of such instructional screens is
> often unnecessary, there are times when it is helpful to the user to get a nudge in the right
> direction.

> Users cannot be expected to read a manual before using your app. People do not launch an app to
> spend time learning how to use the interface, but rather to complete a task in as short an amount
> of time as possible.

> Bombarding users with frequent hint screens causes them to dismiss hints more quickly, regardless
> of how helpful each may be.

> Showing multiple coach marks or tips in a row not only creates problems with users' short-term
> memory, but can also make your app appear overly complicated and daunting to new users. This alone
> may be enough to dissuade them from using your app.

> People must immediately be able to distinguish between hint screens and actual elements of the
> interface. If it is not completely obvious that the tips are simply annotating the interface,
> people will sometimes get confused and try to interact with the tips.

> Presenting hints one-by-one, at the right moment, makes it a lot easier for users to understand and
> learn instructions.

URL: https://www.nngroup.com/articles/mobile-instructional-overlay/

**When to use:** one unfamiliar gesture or control, shown at the moment it becomes relevant.
**When NOT to use:** to define a noun, and never as the only place a concept is explained.
**Accessibility notes:** an overlay is a focus trap problem. It needs a focus target on open, an
Escape route, a visible dismiss control, restored focus on close, and a way to bring it back. NN/g's
own contextual help guidance makes recall mandatory: "Being able to get rid of a (momentarily)
unhelpful overlay is absolutely critical, but so is the ability to find this information again later."

## Summary table

| Product | Surface | What it teaches | Cost to build in plain HTML |
| --- | --- | --- | --- |
| GitHub | Docs page titled with the user's question | Full concept | None, it is a link |
| Notion | Help doc, definition in sentence one | Full concept | None, it is a link |
| Elastic | Task sentence that carries the definition as a clause | Just enough | Very low, it is prose |
| Grafana | Empty state with definition paragraph plus docs link plus one button | Full concept, in place | Low, static markup |
| Stripe | Persistent banner or notification box | Consequence of a mode | Low, static markup |
| Linear | Demo workspace with throwaway state | Whole product model | High, needs sample data |
| Coach marks | First-run overlay | One gesture | High, focus management |
