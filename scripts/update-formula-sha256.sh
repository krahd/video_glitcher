#!/bin/sh
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [--apply] <formula-file> <asset-file> [<asset-file>...]

Computes SHA256 for each given asset file and prints the replacement perl
command to update the matching `sha256` entry in the Homebrew formula. Use
`--apply` to modify the formula in-place (a backup <formula-file>.bak is created).

Example (dry-run):
  $0 Formula/video_glitcher.rb dist/video_glitcher-macos-aarch64.zip

Example (apply):
  $0 --apply Formula/video_glitcher.rb dist/video_glitcher-macos-aarch64.zip
EOF
}

if [ "$#" -lt 2 ]; then
  usage
  exit 1
fi

apply=0
if [ "$1" = "--apply" ]; then
  apply=1
  shift
fi

formula="$1"
shift

if [ ! -f "$formula" ]; then
  echo "Formula not found: $formula" >&2
  exit 2
fi

for asset in "$@"; do
  if [ ! -f "$asset" ]; then
    echo "Asset not found: $asset" >&2
    exit 3
  fi

  sha=$(shasum -a 256 "$asset" | awk '{print $1}')
  base=$(basename "$asset" | sed -E 's/\./\\./g')

  echo "Asset: $asset"
  echo "SHA256: $sha"

  # Prepare a perl one-liner that replaces the sha256 value for the matching url+sha256 pair.
  perl_cmd="perl -0777 -i.bak -pe 's|(url\s+\"[^\n\"]*$base\"\s*\n\s*sha256\s+\")([0-9a-f]{64})(\")|\$1$sha\$3|g' $formula"

  if [ $apply -eq 1 ]; then
    echo "Applying update to $formula (backup: $formula.bak)"
    # Execute perl replacement
    perl -0777 -i.bak -pe "s|(url\s+\"[^\n\"]*$base\"\s*\n\s*sha256\s+\")([0-9a-f]{64})(\")|\$1$sha\$3|g" "$formula"
  else
    echo "Dry-run: to apply, run:" 
    echo "  $perl_cmd"
  fi

  echo
done

exit 0
