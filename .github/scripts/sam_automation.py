import os
import subprocess
import json
import re
import shutil
import glob
import argparse
import time
from pathlib import Path
from datetime import datetime

# Configuration
AI_BRANCH_PATTERNS = [
    r'^feat/.*',
    r'^feature/.*',
    r'^fix/.*',
    r'^infra/.*',
    r'^docs/.*'
]
GH_PAGES_WORKTREE = "/tmp/batmon-gh-pages"
SAM_OUT_DIR = "/tmp/sam-agent-runs"
PROJECT_NAME = "batmon"

class Logger:
    @staticmethod
    def log(tag, message):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"{timestamp} [{tag}] {message}")

    @staticmethod
    def info(message): Logger.log("INFO", message)
    @staticmethod
    def debug(message): Logger.log("DEBUG", message)
    @staticmethod
    def warn(message): Logger.log("WARN", message)
    @staticmethod
    def error(message): Logger.log("ERROR", message)
    @staticmethod
    def perf(message): Logger.log("PERF", message)
    @staticmethod
    def dry_run(message): Logger.log("DRY-RUN", message)
    @staticmethod
    def exec(message): Logger.log("EXEC", message)

class Timer:
    def __init__(self):
        self.metrics = {}

    def start(self, label):
        self.metrics[label] = {"start": time.time()}

    def stop(self, label):
        if label in self.metrics:
            self.metrics[label]["end"] = time.time()
            duration = self.metrics[label]["end"] - self.metrics[label]["start"]
            self.metrics[label]["duration"] = duration
            Logger.perf(f"{label} finished in {duration:.2f}s")

    def get_summary(self):
        summary = "\n" + "-"*50 + "\n"
        summary += "SAM Automation Performance Summary:\n"
        total_duration = 0
        for label, data in self.metrics.items():
            if "duration" in data and label != "TOTAL_WORKFLOW":
                summary += f"- {label}: {data['duration']:.2f}s\n"
                total_duration += data['duration']
        if "TOTAL_WORKFLOW" in self.metrics and "duration" in self.metrics["TOTAL_WORKFLOW"]:
            summary += f"- Total Workflow Time: {self.metrics['TOTAL_WORKFLOW']['duration']:.2f}s\n"
        summary += "-"*50
        return summary

timer = Timer()

def run_command(cmd, cwd=None, env=None, check=True, dry_run=False, log_cmd=True):
    if log_cmd:
        Logger.exec(f"{' '.join(cmd)}")
    
    if dry_run and any(x in cmd for x in ["push", "comment", "PATCH", "POST"]):
        Logger.dry_run(f"Skipping mutation command: {' '.join(cmd)}")
        return subprocess.CompletedProcess(cmd, 0, stdout="", stderr="")

    result = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True, text=True)
    if check and result.returncode != 0:
        Logger.error(f"Command failed (Code {result.returncode})")
        Logger.debug(f"STDOUT: {result.stdout}")
        Logger.debug(f"STDERR: {result.stderr}")
        raise RuntimeError(f"Command failed with return code {result.returncode}")
    return result

def get_short_sha(sha):
    return sha[:7]

def find_latest_report(out_dir):
    pattern = os.path.join(out_dir, "**", "agent-report.json")
    files = glob.glob(pattern, recursive=True)
    if not files:
        raise RuntimeError(f"No agent-report.json found in {out_dir}")
    
    # Sort by modification time
    files.sort(key=os.path.getmtime, reverse=True)
    return os.path.dirname(files[0])

def prune_reports(pages_dir, env):
    timer.start("PRUNING")
    Logger.info("Pruning old reports...")
    sam_reports_dir = os.path.join(pages_dir, "sam-reports")
    if not os.path.exists(sam_reports_dir):
        timer.stop("PRUNING")
        return

    # 1. Prune PR reports
    pr_dirs = [d for d in os.listdir(sam_reports_dir) if d.startswith("pr-")]
    for pr_dir in pr_dirs:
        pr_path = os.path.join(sam_reports_dir, pr_dir)
        pr_id = pr_dir.replace("pr-", "")
        
        # Check PR status
        try:
            status_cmd = ["gh", "pr", "view", pr_id, "--json", "state"]
            status_result = run_command(status_cmd, env=env, check=False, log_cmd=False)
            if status_result.returncode == 0:
                pr_status = json.loads(status_result.stdout).get("state")
                if pr_status in ["MERGED", "CLOSED"]:
                    Logger.info(f"Pruning closed PR report: {pr_dir}")
                    shutil.rmtree(pr_path)
                    continue
            else:
                Logger.warn(f"Could not check status for PR {pr_id}, skipping prune.")
        except Exception as e:
            Logger.error(f"Error checking PR {pr_id}: {e}")
            continue

        # 2. Limit SHA reports for active PRs (keep top 3 most recent)
        sha_dirs = []
        for d in os.listdir(pr_path):
            d_path = os.path.join(pr_path, d)
            if os.path.isdir(d_path) and d != "latest" and len(d) == 7: # short sha
                sha_dirs.append(d_path)
        
        if len(sha_dirs) > 3:
            sha_dirs.sort(key=os.path.getmtime, reverse=True)
            to_delete = sha_dirs[3:]
            for d in to_delete:
                Logger.info(f"Pruning old SHA report in {pr_dir}: {os.path.basename(d)}")
                shutil.rmtree(d)

    # 3. Prune old branch reports (older than 7 days)
    branch_dirs = [d for d in os.listdir(sam_reports_dir) if d.startswith("branch-")]
    now = time.time()
    for b_dir in branch_dirs:
        b_path = os.path.join(sam_reports_dir, b_dir)
        if os.path.isdir(b_path):
            mtime = os.path.getmtime(b_path)
            if (now - mtime) > (7 * 24 * 3600):
                Logger.info(f"Pruning old branch report: {b_dir}")
                shutil.rmtree(b_path)
    timer.stop("PRUNING")

def parse_args():
    parser = argparse.ArgumentParser(description="SAM Automation Script")
    parser.add_argument("--dry-run", action="store_true", help="Run without pushing or commenting")
    return parser.parse_args()

def main():
    args = parse_args()
    timer.start("TOTAL_WORKFLOW")
    
    # 0. Setup environment
    github_ref = os.environ.get("GITHUB_REF", "")
    github_sha = os.environ.get("GITHUB_SHA", "")
    github_token = os.environ.get("GITHUB_TOKEN", "")
    github_event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    
    if not github_ref or not github_sha:
        Logger.error("GITHUB_REF and GITHUB_SHA must be set.")
        return

    if github_ref.startswith("refs/heads/"):
        branch_name = github_ref.replace("refs/heads/", "")
    else:
        branch_name = github_ref
        
    Logger.info(f"--- SAM Automation Start ---")
    Logger.info(f"Event: {github_event_name}")
    Logger.info(f"Branch: {branch_name}")
    Logger.info(f"SHA: {github_sha}")
    
    is_pr_closed_event = (github_event_name == "pull_request")
    
    if args.dry_run:
        Logger.dry_run("RUNNING IN DRY-RUN MODE")
    
    # Check if branch matches AI patterns
    matched = any(re.match(pattern, branch_name) for pattern in AI_BRANCH_PATTERNS)
    if not matched:
        Logger.info(f"Branch {branch_name} does not match AI patterns. Skipping.")
        return

    env = os.environ.copy()
    if github_token:
        env["GH_TOKEN"] = github_token
        env["GITHUB_TOKEN"] = github_token
    
    # 1. Identify PR
    pr_number = None
    try:
        timer.start("IDENTIFY_PR")
        pr_view_cmd = ["gh", "pr", "view", branch_name, "--json", "number,headRefName,url"]
        pr_result = run_command(pr_view_cmd, env=env)
        pr_data = json.loads(pr_result.stdout)
        pr_number = pr_data["number"]
        Logger.info(f"Detected PR #{pr_number}")
        timer.stop("IDENTIFY_PR")
    except Exception as e:
        Logger.warn(f"Could not find active PR for branch {branch_name}.")

    # 2. Run SAM metrics
    report_dir = None
    if not is_pr_closed_event:
        timer.start("SAM_ANALYSIS")
        Logger.info("Running SAM metrics...")
        if os.path.exists(SAM_OUT_DIR):
            shutil.rmtree(SAM_OUT_DIR)
        os.makedirs(SAM_OUT_DIR, exist_ok=True)
        
        sam_cmd = [
            "sam-agent-metrics",
            "--project", os.getcwd(),
            "--out", SAM_OUT_DIR,
            "--project-name", PROJECT_NAME,
            "--language", "KOTLIN",
            "--mode", "svace",
            "--build-command", "./gradlew --no-daemon clean :app:assembleDebug"
        ]
        run_command(sam_cmd, env=env)
        report_dir = find_latest_report(SAM_OUT_DIR)
        Logger.info(f"Latest report found at: {report_dir}")
        timer.stop("SAM_ANALYSIS")
    else:
        Logger.info("PR closed event detected. Skipping SAM analysis, proceeding to pruning.")

    # 4. Prepare Pages worktree
    timer.start("PREPARE_WORKTREE")
    Logger.info("Preparing gh-pages worktree...")
    run_command(["git", "config", "user.name", "github-actions[bot]"], env=env, check=False)
    run_command(["git", "config", "user.email", "github-actions[bot]@users.noreply.github.com"], env=env, check=False)
    
    timer.start("GIT_FETCH_PAGES")
    run_command(["git", "fetch", "--depth", "1", "origin", "gh-pages"], env=env, check=False)
    timer.stop("GIT_FETCH_PAGES")
    
    if os.path.exists(GH_PAGES_WORKTREE):
        Logger.info(f"Cleaning up existing directory {GH_PAGES_WORKTREE}...")
        run_command(["git", "worktree", "remove", "--force", GH_PAGES_WORKTREE], env=env, check=False)
        if os.path.exists(GH_PAGES_WORKTREE):
            shutil.rmtree(GH_PAGES_WORKTREE, ignore_errors=True)
    
    try:
        run_command(["git", "worktree", "add", GH_PAGES_WORKTREE, "origin/gh-pages"], env=env)
    except:
        Logger.warn("gh-pages branch not found or failed to add. Creating orphan branch...")
        if os.path.exists(GH_PAGES_WORKTREE):
            shutil.rmtree(GH_PAGES_WORKTREE, ignore_errors=True)
        run_command(["git", "worktree", "add", "--detach", GH_PAGES_WORKTREE], env=env)
        run_command(["git", "-C", GH_PAGES_WORKTREE, "checkout", "--orphan", "gh-pages"], env=env)
        run_command(["git", "-C", GH_PAGES_WORKTREE, "rm", "-rf", "."], env=env)
    timer.stop("PREPARE_WORKTREE")

    # 4.5 Prune old reports
    prune_reports(GH_PAGES_WORKTREE, env)

    # 5. Copy report to gh-pages
    if report_dir:
        timer.start("COPY_REPORTS")
        Logger.info("Copying reports to gh-pages...")
        short_sha = get_short_sha(github_sha)
        
        if pr_number:
            base_report_path = os.path.join(GH_PAGES_WORKTREE, "sam-reports", f"pr-{pr_number}")
        else:
            base_report_path = os.path.join(GH_PAGES_WORKTREE, "sam-reports", f"branch-{branch_name}")

        sha_path = os.path.join(base_report_path, short_sha)
        latest_path = os.path.join(base_report_path, "latest")

        for dest in [sha_path, latest_path]:
            if os.path.exists(dest):
                shutil.rmtree(dest)
            os.makedirs(dest, exist_ok=True)
            
            shutil.copy(os.path.join(report_dir, "dev-report.html"), os.path.join(dest, "index.html"))
            shutil.copy(os.path.join(report_dir, "agent-report.md"), dest)
            shutil.copy(os.path.join(report_dir, "agent-report.json"), dest)
            
            html_src = os.path.join(report_dir, "attempts", "svace", "sam-result", "html")
            if os.path.exists(html_src):
                shutil.copytree(html_src, os.path.join(dest, "attempts", "svace", "sam-result", "html"))
        timer.stop("COPY_REPORTS")

    # 6. Commit and Push
    timer.start("PUSH_PAGES")
    Logger.info("Handling gh-pages commit/push...")
    run_command(["git", "-C", GH_PAGES_WORKTREE, "add", "."], env=env)
    commit_msg = f"docs: publish SAM report for {branch_name}"
    if pr_number:
        commit_msg = f"docs: publish SAM report for PR {pr_number}"
    
    run_command(["git", "-C", GH_PAGES_WORKTREE, "commit", "-m", commit_msg], env=env, check=False)
    
    push_cmd = ["git", "-C", GH_PAGES_WORKTREE, "push", "origin", "HEAD:gh-pages"]
    run_command(push_cmd, env=env, dry_run=args.dry_run)
    timer.stop("PUSH_PAGES")

    # 7. Update PR comment
    if pr_number and report_dir:
        timer.start("UPDATE_PR_COMMENT")
        Logger.info(f"Updating PR #{pr_number} comment...")
        
        with open(os.path.join(report_dir, "agent-report.md"), "r") as f:
            summary_content = f.read()

        repo_url_result = run_command(["git", "remote", "get-url", "origin"], env=env)
        repo_url = repo_url_result.stdout.strip()
        match = re.search(r"github\.com[:/](.+)\.git", repo_url)
        owner_repo = match.group(1) if match else "TrdHuy/batmon"
        owner = owner_repo.split("/")[0]
        short_sha = get_short_sha(github_sha)
        
        latest_url = f"https://{owner}.github.io/{PROJECT_NAME}/sam-reports/pr-{pr_number}/latest/"
        sha_url = f"https://{owner}.github.io/{PROJECT_NAME}/sam-reports/pr-{pr_number}/{short_sha}/"

        comment_body = f"""<!-- sam-agent-metrics-report -->

## SAM Agent Metrics Report

Commit: `{github_sha}`
Mode: `svace`

{summary_content}

HTML report:
{latest_url}

Commit-specific report:
{sha_url}
"""
        
        comments_result = run_command(["gh", "api", f"repos/{owner_repo}/issues/{pr_number}/comments"], env=env, log_cmd=False)
        comments = json.loads(comments_result.stdout)
        
        existing_comment_id = next((c["id"] for c in comments if "<!-- sam-agent-metrics-report -->" in c["body"]), None)
        
        if existing_comment_id:
            Logger.info(f"Updating existing comment {existing_comment_id}...")
            payload = json.dumps({"body": comment_body})
            with open("/tmp/sam-payload.json", "w") as f: f.write(payload)
            patch_cmd = ["gh", "api", "--method", "PATCH", f"repos/{owner_repo}/issues/comments/{existing_comment_id}", "--input", "/tmp/sam-payload.json"]
            run_command(patch_cmd, env=env, dry_run=args.dry_run)
        else:
            Logger.info("Creating new comment...")
            with open("/tmp/sam-comment-body.md", "w") as f: f.write(comment_body)
            comment_cmd = ["gh", "pr", "comment", str(pr_number), "--body-file", "/tmp/sam-comment-body.md"]
            run_command(comment_cmd, env=env, dry_run=args.dry_run)
        timer.stop("UPDATE_PR_COMMENT")

    timer.stop("TOTAL_WORKFLOW")
    Logger.info("--- SAM Automation Finished Successfully ---")
    print(timer.get_summary())

if __name__ == "__main__":
    main()
