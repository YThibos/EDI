Craftsmanship rules this change must meet; review it against them as acceptance criteria:
- Clean Code and domain-driven design are the bar: quality first, no shortcuts or "good enough for now", and maintainability outweighs short-term speed.
- Names, boundaries, and abstractions carry the domain's language, not the mechanism's.
- Methods are short and do one thing at one level of abstraction.
- Main and public methods read as a table of contents: a sequence of named steps, with the detail below.
- Private helpers sit below their callers, ordered by first use, so the file reads top-down.
- Business-meaningful mappings are extracted into a well-named method even when they are one-liners.
- Writing: plain "-" never an em dash, United Kingdom English outside syntax and vendor terms, and one sentence per line in Markdown.
- No machine-generated tells: comments that restate the code, unrequested defensive boilerplate, docstrings narrating the implementation, a comment on every block, or an abstraction with one implementation and no second caller.
