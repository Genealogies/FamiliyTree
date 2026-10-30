#!/usr/bin/env python3
"""Upload failing iOS screenshot diffs and post/update a pull request comment.

Runs after the Android (Paparazzi) or iOS screenshot verification step,
regardless of whether it passed. If no screen differs it removes any stale
diff comment left by a previous run on this PR; otherwise it uploads the
expected/actual/diff images via GitHub's user-attachments API -- the same
endpoint the web UI uses for drag-and-drop image uploads in issues/PRs, so no
separate image host or secrets are required -- and posts one comment per
platform, identified by an HTML marker so later runs update it in place
instead of piling up duplicates.

Note: for pull requests from forks, the default GITHUB_TOKEN is read-only,
so posting the comment will fail. That failure is swallowed (a warning is
printed) rather than failing the job, because the dedicated verification
step already enforces pass/fail for the screenshot check itself.
"""

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def marker(platform: str) -> str:
    return f"<!-- screenshot-diff:{platform} -->"


def collect_ios(output_dir: Path):
    """Read scripts/ios-screenshots.py's result.json and locate its images."""
    result_path = output_dir / "result.json"
    if not result_path.is_file():
        return []
    failures = json.loads(result_path.read_text()).get("failures", [])
    entries = []
    for failure in failures:
        name = failure.split(":", 1)[0].strip()
        detail = failure.split(":", 1)[1].strip() if ":" in failure else failure
        actual = output_dir / f"{name}.png"
        expected = output_dir / f"{name}-expected.png"
        diff = output_dir / f"{name}-diff.png"
        entries.append(
            {
                "name": name,
                "detail": detail,
                "expected": expected if expected.is_file() else None,
                "actual": actual if actual.is_file() else None,
                "diff": diff if diff.is_file() else None,
            }
        )
    return entries


def upload(path: Path, repo_id: str, token: str) -> str:
    query = urllib.parse.urlencode(
        {"name": path.name, "content_type": "image/png", "repository_id": repo_id}
    )
    request = urllib.request.Request(
        f"https://uploads.github.com/user-attachments/assets?{query}",
        data=path.read_bytes(),
        method="POST",
        headers={
            "Content-Type": "application/octet-stream",
            "X-GitHub-Api-Version": "2022-11-28",
            "Authorization": f"Bearer {token}",
        },
    )
    with urllib.request.urlopen(request) as response:
        body = json.loads(response.read())
    url = body.get("url", "")
    if not url.startswith("https://"):
        raise RuntimeError(f"upload failed for {path.name}: {body}")
    return url


def build_body(platform: str, entries, urls: dict) -> str:
    lines = [
        marker(platform),
        f"### \U0001f4f8 Screenshot differences detected ({platform})",
        "",
        "| Screen | Expected | Actual | Diff |",
        "| --- | --- | --- | --- |",
    ]
    for entry in entries:
        name = entry["name"]
        cells = []
        for label in ("expected", "actual", "diff"):
            url = urls.get((name, label))
            cells.append(f"![{label}]({url})" if url else "–")
        lines.append(f"| **{name}**<br>{entry['detail']} | {cells[0]} | {cells[1]} | {cells[2]} |")
    return "\n".join(lines)


def gh_api(*args, input_text=None):
    return subprocess.run(
        ["gh", "api", *args],
        check=True,
        capture_output=True,
        text=True,
        input=input_text,
    ).stdout


def find_existing_comment(repo: str, pr: int, platform: str):
    raw = gh_api(f"repos/{repo}/issues/{pr}/comments", "--jq", "[.[] | {id, body}]")
    for comment in json.loads(raw):
        if marker(platform) in comment["body"]:
            return comment["id"]
    return None


def upsert_comment(repo: str, pr: int, platform: str, body: str):
    existing_id = find_existing_comment(repo, pr, platform)
    if existing_id is not None:
        gh_api(f"repos/{repo}/issues/comments/{existing_id}", "-X", "PATCH", "-f", f"body={body}")
    else:
        gh_api(f"repos/{repo}/issues/{pr}/comments", "-X", "POST", "-f", f"body={body}")


def delete_stale_comment(repo: str, pr: int, platform: str):
    existing_id = find_existing_comment(repo, pr, platform)
    if existing_id is not None:
        gh_api(f"repos/{repo}/issues/comments/{existing_id}", "-X", "DELETE")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True, choices=["ios"])
    parser.add_argument("--repo", required=True)
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--ios-output-dir", type=Path)
    args = parser.parse_args()

    entries = collect_ios(args.ios_output_dir)

    try:
        if not entries:
            delete_stale_comment(args.repo, args.pr, args.platform)
            print(f"No {args.platform} screenshot differences; nothing to report.")
            return 0

        import os

        token = os.environ["GITHUB_TOKEN"]
        repo_id = gh_api(f"repos/{args.repo}", "--jq", ".id").strip()

        urls = {}
        for entry in entries:
            for label in ("expected", "actual", "diff"):
                path = entry.get(label)
                if path is not None:
                    urls[(entry["name"], label)] = upload(path, repo_id, token)

        body = build_body(args.platform, entries, urls)
        upsert_comment(args.repo, args.pr, args.platform, body)
        print(f"Posted {len(entries)} screenshot diff(s) to PR #{args.pr}.")
    except (subprocess.CalledProcessError, urllib.error.URLError, KeyError) as error:
        # Posting the comment is best-effort: the dedicated verification step
        # already fails the job on a real diff, so a reporting hiccup (e.g.
        # the default token being read-only on a fork PR) must not also fail
        # the job.
        print(f"Warning: could not report screenshot diffs: {error}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
