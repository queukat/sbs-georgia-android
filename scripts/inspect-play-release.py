"""Read Play release metadata without committing an edit. Requires google-auth and requests."""
import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    # Deliberately independent of PLAY_PACKAGE_NAME, which may belong to another app.
    package = "com.queukat.sbsgeorgia"
    credentials = Credentials.from_service_account_file(
        os.environ["PLAY_KEY_FILE"],
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)
    base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}"
    response = session.post(f"{base}/edits", json={}, timeout=30)
    response.raise_for_status()
    edit = response.json()["id"]
    edit_url = f"{base}/edits/{edit}"
    snapshot = {"package": package, "checkedAt": datetime.now(timezone.utc).isoformat()}
    try:
        for resource in ("tracks", "bundles", "apks", "listings"):
            response = session.get(f"{edit_url}/{resource}", timeout=30)
            response.raise_for_status()
            snapshot[resource] = response.json()
        snapshot["screenshots"] = {}
        for listing in snapshot["listings"].get("listings", []):
            locale = listing["language"]
            response = session.get(f"{edit_url}/listings/{locale}/phoneScreenshots", timeout=30)
            response.raise_for_status()
            snapshot["screenshots"][locale] = response.json()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
        codes = [int(item["versionCode"]) for key in ("bundles", "apks")
                 for item in snapshot[key].get(key, [])]
        for track in snapshot["tracks"].get("tracks", []):
            for release in track.get("releases", []):
                codes.extend(int(code) for code in release.get("versionCodes", []))
                print(track["track"], release.get("name"), release.get("versionCodes"), release["status"])
        print("Maximum uploaded versionCode:", max(codes, default=0))
        print("Snapshot:", args.output)
    finally:
        response = session.delete(edit_url, timeout=30)
        response.raise_for_status()
        session.close()


if __name__ == "__main__":
    main()
