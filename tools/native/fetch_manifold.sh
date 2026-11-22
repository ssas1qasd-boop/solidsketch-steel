#!/usr/bin/env bash
# Fetches Manifold (Apache-2.0) at the pinned commit into third_party/manifold (git-ignored).
# Used by the Gradle task :fetchManifold and by host builds of native/tests.
set -euo pipefail
TAG=v3.5.4
COMMIT=ce50d78021d64507f89e8c9fc2c2e51018117857
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/third_party/manifold"
if [ -d "$DIR/.git" ] && [ "$(git -C "$DIR" rev-parse HEAD)" = "$COMMIT" ]; then exit 0; fi
rm -rf "$DIR"
mkdir -p "$ROOT/third_party"
for i in 1 2 3 4; do
  if git -c advice.detachedHead=false clone -q --depth 1 --branch "$TAG" https://github.com/elalish/manifold.git "$DIR"; then break; fi
  rm -rf "$DIR"; sleep $((i * 2))
done
HEAD="$(git -C "$DIR" rev-parse HEAD)"
if [ "$HEAD" != "$COMMIT" ]; then echo "Manifold $TAG is $HEAD, expected $COMMIT" >&2; exit 1; fi
echo "Manifold $TAG ($COMMIT) in $DIR"
