# Rewritten pull request texts

Each file here holds the new title and description of one closed pull request, named by its number: the first line is the title after `# `, and everything after the blank line below it is the description.
Every description is a neutral summary of what the change does, built from the pull request's own commits and its backlog item's goal, and of how it was verified, so it names no host, address, port, account, path, project or ticket outside this repository.
The open pull requests are left out; their descriptions were written to the same standard, and the publish check reads every new text before EDI posts it ([publish-guard](../../decisions/publish-guard.md)).

## Applying them

Run this from a checkout of the repository, on the captain's machine, with `gh` logged in to the account that owns it:

```sh
cd docs/publication/pull-requests
for file in [0-9][0-9][0-9].md; do
  number=$((10#${file%.md}))
  title=$(head -n 1 "$file" | sed 's/^# //')
  tail -n +3 "$file" | gh pr edit "$number" --title "$title" --body-file -
done
```

Then open each pull request in the web interface and delete every older revision from its description's edit history, which the API cannot do.
