# JIRA: the calls EDI makes

Verified on a JIRA Server 11.3 instance (`/rest/api/2/serverInfo`), 17/10/2026, from WSL2 Ubuntu 24.04 behind an HTTP proxy, with a personal access token read from the keys file at each call.
Every call against the real instance was read-only; creating, filling, transitioning, placing in a sprint and linking are exercised against a stand-in in the tests, never against the instance.

| Call | Result on JIRA Server 11.3 |
|---|---|
| `GET /rest/api/2/issue/<key>?fields=summary,status` with `Authorization: Bearer <token>` | Prints the ticket's `key`, `fields.summary` and `fields.status.name`. |
| `GET /rest/api/2/search?jql=project = "<KEY>" AND statusCategory != Done AND text ~ "<words>" ORDER BY updated DESC` | Prints `issues`, each with `key` and the requested fields; spaces in the query are sent as `%20`. |
| `GET /rest/api/2/issue/<key>/transitions` | Prints `transitions`, each with `id`, `name` and `to.name`; a transition's name and its target status differ, and the target status can be written in another case than the workflow's own list, so EDI compares statuses without regard to case. |
| `GET /rest/agile/1.0/issue/<key>?fields=sprint` | Prints `fields.sprint` as an object with `name` and `state` while the ticket is in an active sprint. |
| `GET /rest/agile/1.0/board?projectKeyOrId=<KEY>&type=scrum` | Prints `values`, the project's scrum boards, each with its `id`. |
| `GET /rest/agile/1.0/board/<id>/sprint?state=active` | Prints `values`, the board's active sprints, each with `id` and `name`. |

The HTTP client goes through the proxy that `https_proxy` names and straight to the hosts `no_proxy` lists, as the JDK does not read those variables itself.
EDI's adapter is `adapter.tracker.JiraTracker` with `JiraCalls` ([trackers-and-time-booking](../decisions/trackers-and-time-booking.md)); `JiraLiveCheckTest`, tagged `live`, repeats the ticket read and the search against the instance an EDI home names in `EDI_LIVE_HOME`, for the ticket in `EDI_LIVE_TICKET`.
