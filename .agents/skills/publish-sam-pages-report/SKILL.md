---
name: publish-sam-pages-report
description: Publish a local sam-agent-metrics HTML report to the repository GitHub Pages branch and update the related PR with a Markdown summary plus browser links. Use after creating or updating a PR when the user wants SAM metrics visible on GitHub without GitHub Actions.
---

# Publish SAM Pages Report

## Use When
- A PR has just been created or updated and the user wants a SAM metrics report attached to it.
- The user asks to publish, update, or comment a `sam-agent-metrics` report on a PR.
- The user wants a browser-viewable HTML report on GitHub without GitHub Actions.

## Preconditions
- Run from the project root.
- `sam-agent-metrics`, `gh`, `git`, and `zip` are available.
- The feature branch is already pushed and associated with a GitHub PR.
- GitHub Pages is configured to serve from branch `gh-pages`, root directory.

## Rules
- Do not commit SAM reports into the feature/source branch.
- Do not switch the current worktree away from the user's active branch.
- Use `git worktree` for the Pages branch.
- Use `/tmp/sam-agent-runs` for SAM output and `/tmp/batmon-gh-pages` for the Pages worktree unless there is a conflict.
- Update the existing PR comment when the marker exists. Create a new comment only when no marker exists.
- Keep the PR comment concise: summary first, then links.
- Publish only the HTML assets required by `dev-report.html`; do not publish the full `raw-sam` or full `attempts` directory unless the user explicitly asks for deep raw output.
- If `gh-pages` does not exist, create it as an orphan branch in the temporary worktree.
- If GitHub Pages is not enabled, still push `gh-pages` and report that the repo setting must be enabled.

## Output Layout
Publish reports to `gh-pages` using this layout:

```text
sam-reports/
  pr-<PR_NUMBER>/
    <short_sha>/
      index.html
      agent-report.md
      agent-report.json
      attempts/svace/sam-result/html/
    latest/
      index.html
      agent-report.md
      agent-report.json
      attempts/svace/sam-result/html/
```

Use these URL shapes in the PR comment:

```text
https://trdhuy.github.io/batmon/sam-reports/pr-<PR_NUMBER>/latest/
https://trdhuy.github.io/batmon/sam-reports/pr-<PR_NUMBER>/<short_sha>/
```

## Workflow
1. Confirm the current branch and PR:
   ```bash
   git rev-parse --abbrev-ref HEAD
   git rev-parse HEAD
   gh pr view --json number,headRefName,headRefOid,url
   ```

2. Run SAM metrics:
   ```bash
   sam-agent-metrics \
     --project "$(pwd)" \
     --out /tmp/sam-agent-runs \
     --project-name batmon \
     --language KOTLIN \
     --mode svace \
     --build-command "./gradlew --no-daemon clean :app:assembleDebug"
   ```

3. Identify the newest successful run directory:
   ```bash
   find /tmp/sam-agent-runs -name agent-report.json -printf '%T@ %h\n' | sort -nr | head -1
   ```

4. Prepare the Pages worktree:
   ```bash
   git fetch origin gh-pages || true
   git worktree remove --force /tmp/batmon-gh-pages 2>/dev/null || true
   git worktree add /tmp/batmon-gh-pages origin/gh-pages
   ```

   If `origin/gh-pages` does not exist:
   ```bash
   git worktree add --detach /tmp/batmon-gh-pages
   cd /tmp/batmon-gh-pages
   git checkout --orphan gh-pages
   git rm -rf .
   ```

5. Copy the report:
   - Copy `dev-report.html` to `index.html`.
   - Copy `agent-report.md`.
   - Copy `agent-report.json`.
   - Copy only the HTML report directory needed by the redirect in `dev-report.html`, normally `attempts/svace/sam-result/html/`.
   - Do not copy the full `attempts/` directory; it can be much larger than the browser report.
   - Write both commit-specific and `latest` folders.

6. Commit and push `gh-pages`:
   ```bash
   git -C /tmp/batmon-gh-pages add sam-reports/pr-<PR_NUMBER>
   git -C /tmp/batmon-gh-pages commit -m "docs: publish SAM report for PR <PR_NUMBER> <short_sha>"
   git -C /tmp/batmon-gh-pages push origin HEAD:gh-pages
   ```

7. Build the PR comment body with this marker:
   ```md
   <!-- sam-agent-metrics-report -->

   ## SAM Agent Metrics Report

   Commit: `<full_sha>`
   Mode: `svace`

   <summary from agent-report.md>

   HTML report:
   https://trdhuy.github.io/batmon/sam-reports/pr-<PR_NUMBER>/latest/

   Commit-specific report:
   https://trdhuy.github.io/batmon/sam-reports/pr-<PR_NUMBER>/<short_sha>/
   ```

8. Update or create the PR comment:
   - Fetch comments with `gh api repos/TrdHuy/batmon/issues/<PR_NUMBER>/comments`.
   - Find the comment containing `<!-- sam-agent-metrics-report -->`.
   - Update it with a JSON payload so multiline Markdown is sent as the comment body:
     ```bash
     jq -n --arg body "$(cat "$BODY_FILE")" '{body: $body}' > /tmp/sam-comment-payload.json
     gh api --method PATCH \
       repos/TrdHuy/batmon/issues/comments/<COMMENT_ID> \
       --input /tmp/sam-comment-payload.json
     ```
   - If no marker exists, create it with `gh pr comment <PR_NUMBER> --body-file <body_file>`.

## Verification
- Confirm `gh-pages` pushed successfully.
- Open or report the `latest` Pages URL.
- Confirm the PR comment points to the current commit SHA.
- Leave the active feature worktree on its original branch.
