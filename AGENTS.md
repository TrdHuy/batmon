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

## GitHub CLI Identity Isolation

To prevent identity conflicts between agents or local environments, all `gh` commands must use the project-local configuration directory:

```bash
GH_CONFIG_DIR=.config/gh gh <command>
```

Always ensure this environment variable is set before executing any GitHub CLI operations. The current project-local account is `synclab-automation-system-gemini`.
