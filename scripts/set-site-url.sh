#!/usr/bin/env bash
# Repoints every absolute URL on the site at a new host.
#
#   ./scripts/set-site-url.sh https://spenguard.com
#   ./scripts/set-site-url.sh https://spenguard.netlify.app
#
# Relative links between pages already work anywhere; only the canonical,
# Open Graph, JSON-LD, sitemap and robots entries carry an absolute URL.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <new-base-url>    e.g. https://spenguard.com" >&2
  exit 1
fi

NEW="${1%/}/"                       # always exactly one trailing slash
case "$NEW" in
  https://*) ;;
  *) echo "error: the URL must start with https:// - Google Play requires it." >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Discover the base currently in use, rather than assuming it.
OLD=$(grep -ho 'https://[^"]*/' docs/sitemap.xml | head -1)
OLD=$(dirname "$OLD")/
if [ -z "$OLD" ] || [ "$OLD" = "/" ]; then
  echo "error: could not determine the current base URL from docs/sitemap.xml" >&2
  exit 1
fi

echo "current : $OLD"
echo "new     : $NEW"

FILES=$(grep -rl "$OLD" docs PLAY_LISTING.md WEBSITE.md 2>/dev/null || true)
if [ -z "$FILES" ]; then
  echo "nothing to change - the site already points at $NEW"
  exit 0
fi

for f in $FILES; do
  # '|' as the delimiter so the slashes in the URLs need no escaping
  sed -i "s|$OLD|$NEW|g" "$f"
  echo "updated $f"
done

echo
echo "Done. Remaining references to the old host (should be none):"
grep -rn "$OLD" docs PLAY_LISTING.md WEBSITE.md 2>/dev/null || echo "  none"
echo
echo "Next: commit, deploy, then update the Privacy Policy URL in Play Console to"
echo "  ${NEW}privacy.html"
