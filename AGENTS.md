# Agent Rules

## SAM Metrics Report

After creating a PR or pushing a new commit to an active PR, run the
`publish-sam-pages-report` skill.

The required workflow is:
- Run `sam-agent-metrics` for the current PR head.
- Publish the browser-ready HTML report to the `gh-pages` branch.
- Update the PR comment containing `<!-- sam-agent-metrics-report -->` with the
  latest Markdown summary and report links.

Do not commit SAM report output to the feature branch.
