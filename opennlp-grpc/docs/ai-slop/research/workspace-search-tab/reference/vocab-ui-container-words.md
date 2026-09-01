# Vocabulary: how mainstream UIs name temporary and per-user containers

Sources fetched 2026-08-28:

- https://code.visualstudio.com/docs/editing/workspaces/workspaces
- https://learning.postman.com/docs/collaborating-in-postman/using-workspaces/overview/
- https://docs.databricks.com/aws/en/workspace/
- https://slack.com/help/articles/212675257-Join-a-Slack-workspace
- https://help.figma.com/hc/en-us/articles/14381406380183-Guide-to-the-file-browser
- https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests
- https://docs.github.com/en/get-started/writing-on-github/editing-and-sharing-content-with-gists/creating-gists
- https://docs.aws.amazon.com/whitepapers/latest/organizing-your-aws-environment/sandbox-ou.html
- https://research.google.com/colaboratory/faq.html
- https://jupyter-server.readthedocs.io/en/latest/developers/rest-api.html

## "workspace": the most overloaded word in software

Four major products use it, and they mean four different things.

**VS Code**, a set of folders open in one window:

> "A Visual Studio Code _workspace_ is the collection of one or more folders that are
> opened in a VS Code window (instance)."
> (code.visualstudio.com)

> "You don't have to do anything for a folder to become a VS Code workspace other than
> open the folder with VS Code."
> (code.visualstudio.com)

VS Code also has a precedent for the unsaved case, and its word for it is *untitled*,
not *dynamic* or *scratch*:

> "The first time you add a second folder to a workspace, VS Code automatically creates
> an _untitled_ workspace."
> (code.visualstudio.com)

An untitled workspace "will always restore until you save it", and stays unnamed "until
you decide to save it to disk".

**Postman**, an organizational and collaboration scope:

> "Workspaces enable you to organize your Postman work and collaborate with
> teammates."

> "You can group your projects together, with workspace acting as the single source of
> truth for related APIs, collections, environments, mocks, monitors, and other linked
> elements."
> (learning.postman.com)

Postman's workspace types are about *visibility*, not lifecycle: internal, private,
partner, public. "Public workspaces enable you to collaborate on elements with anyone
across the world".

**Databricks**, an entire deployment:

> "The Databricks workspace is your central hub for accessing all Databricks objects
> and features."
> (docs.databricks.com)

**Slack**, an organization:

> "A Slack workspace is made up of channels, where team members can communicate and
> work together."
> (slack.com)

### What this means for the naming decision

Across all four, "workspace" consistently means a *large, long-lived, shared or
account-level scope*: a whole account, a whole team, a whole deployment, a whole
editor window. It never means "one short-lived thing I am building right now". Calling
a single in-memory index a "workspace" runs directly against every mainstream usage,
and calling it a "dynamic workspace" compounds the problem by attaching a lifecycle
adjective to a word that carries no lifecycle meaning.

## "draft": in progress, not yet promoted

**Figma:**

> "Your **Drafts** space contains any files you've created that haven't been moved into
> a folder."

> "Working in a draft is a great way to experiment with different ideas by yourself."

> "If you choose to, you can always share your draft with other people, or move a file
> from your drafts into a folder when you're ready."
> (help.figma.com)

**GitHub draft pull requests:**

> "Draft pull requests cannot be merged, and code owners are not automatically
> requested to review them."

> "When you're ready to get feedback on your pull request, you can mark your draft pull
> request as ready for review."
> (docs.github.com)

Shared semantics of "draft": private by default, incomplete, blocked from the terminal
action, and promoted by an explicit gesture with a name of its own ("move into a
folder", "mark as ready for review").

Fit: partial. "Draft" is about *editorial readiness*, not mutability. It implies "not
usable yet", while the whole point of the live index is that it is usable while being
written.

## "sandbox": isolated, disposable, not for real data

AWS's whitepaper is the crispest published definition:

> "The Sandbox OU contains accounts in which your builders are generally free to
> explore and experiment with AWS services and other tools and services subject to your
> acceptable use policies, and these environments are typically disconnected from your
> internal networks and internal services."
> (docs.aws.amazon.com)

Supporting characteristics from the same whitepaper: "It's common to set expectations
with your builders that the resources they create in sandbox environments are temporary
in nature", "Use of non-public data and intellectual property ... is typically not
allowed in sandbox environments", and "Sandbox accounts should not be promoted to any
other type of account or environment".

Fit for our concept: poor. "Sandbox" carries a strong promise that the contents are
throwaway and *cannot* be promoted. If the concept explicitly supports being made
read-only and kept, "sandbox" tells the user the opposite.

## "session": a live connection with ephemeral state

**Jupyter Server** models a session as the join between a document and a running
kernel: the REST API session object carries `id`, `kernel`, `name`, `path`, and `type`.
(jupyter-server.readthedocs.io)

**Google Colab** ties the ephemeral part to the runtime, not to the notebook:

> "Colab notebooks are stored in Google Drive, or can be loaded from GitHub."

> "Code is executed in a virtual machine private to your account. Virtual machines are
> deleted when idle for a while, and have a maximum lifetime enforced by the Colab
> service."

> "In the version of Colab that is free of charge notebooks can run for at most 12
> hours, depending on availability and your usage patterns."
> (research.google.com/colaboratory/faq.html)

Note the split Colab makes and that our UI probably needs too: the *notebook* persists,
the *runtime* is ephemeral. Two nouns, one durable, one not.

Fit for our concept: "session" says "this will end and its state will be lost". That is
the wrong promise for something you can persist.

## "scratch" and "scratchpad": deliberately disposable side work

Colab exposes a **Scratch code cell** through Insert > Scratch code cell in the product
UI. It is not defined in the official FAQ above, so treat the semantics as observed
rather than documented: scratch cells open in a side panel, run against the same
runtime and variables as the notebook, and are not saved into the notebook body.

Fit for our concept: "scratch" is honest about impermanence and is instantly readable
to any user. It is the strongest of the informal words, but it undersells a container
that can be persisted and searched by other people.

## "gist": a small, shareable, self-contained unit

> "Gists provide a simple way to share code snippets with others. Every gist is a Git
> repository, which means that it can be forked and cloned."
> (docs.github.com)

> "Secret gists don't show up in Discover and are not searchable unless you are logged
> in and are the author."
> (docs.github.com)

Fit: "secret" and "unlisted" are the standard words for private by obscurity, but
"gist" is a GitHub coinage with no generic pull.

## Word-by-word verdict table
| Word | What mainstream UIs mean by it | Fit for "in-memory, being written, later sealed" |
| --- | --- | --- |
| workspace | account, team, deployment, or editor window scope | poor: implies large and permanent, not small and temporary |
| project | a named folder of related files, long-lived | poor for the same reason as workspace |
| draft | private, incomplete, blocked from the final action | partial: implies not usable, but ours is searchable |
| sandbox | isolated, disposable, cannot be promoted | poor: contradicts the ability to persist |
| session | live connection whose state is lost on disconnect | poor: contradicts the ability to persist |
| scratch, scratchpad | side work, deliberately not kept | partial: honest but undersells persistence |
| working set | the subset currently loaded or in use | partial: accurate, but jargon from memory management |
| collection | a named group of items you gathered | good: neutral on lifecycle, matches search industry usage |
| index | the searchable structure itself | good: exact, and the term of art everywhere in search |
| namespace | a scoped slice of one index belonging to one user or job | good if the concept is really scoping, not lifecycle |

## Conclusions for naming

1. Every mainstream "workspace" is bigger and more permanent than the concept here.
   The word will actively mislead.
2. The adjective is doing the work in "dynamic workspace", which is a sign the noun is
   wrong. Users read state badges ("live", "read only") far more reliably than they
   parse an adjective baked into a noun phrase.
3. The search industry has already settled on two nouns for this container, **index**
   and **collection**, and on two states for it, writable and **read only**. Using them
   costs nothing in plainness and buys instant recognition.
4. If the temporary, per-user nature must be visible, express it as a state or scope
   word beside a standard noun ("draft collection", "unsaved index", "personal
   namespace") rather than inventing a new container noun.
