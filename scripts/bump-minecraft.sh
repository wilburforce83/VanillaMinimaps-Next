#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: scripts/bump-minecraft.sh --mc <version> --pack-format <format> [--plugin-version <version>] [--skip-paper-check]

Updates:
- gradle.properties (minecraftVersion, resourcepackPackFormat, optional pluginVersion)
- resourcepack/pack.mcmeta pack_format
- README.MD supported version line

Checks (offline):
- Ensures paper.jar exists in the repo root (unless --skip-paper-check)
- Attempts to read the Minecraft version from paper.jar and compare to --mc
EOF
}

mc_version=""
pack_format=""
plugin_version=""
skip_paper_check=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mc)
      mc_version="${2:-}"
      shift 2
      ;;
    --pack-format)
      pack_format="${2:-}"
      shift 2
      ;;
    --plugin-version)
      plugin_version="${2:-}"
      shift 2
      ;;
    --skip-paper-check)
      skip_paper_check=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$mc_version" || -z "$pack_format" ]]; then
  usage
  exit 1
fi

gradle_properties="$ROOT_DIR/gradle.properties"
pack_mcmeta="$ROOT_DIR/resourcepack/pack.mcmeta"
readme="$ROOT_DIR/README.MD"
paper_jar="$ROOT_DIR/paper.jar"

if [[ ! -f "$gradle_properties" ]]; then
  echo "Missing $gradle_properties" >&2
  exit 1
fi

if [[ ! -f "$pack_mcmeta" ]]; then
  echo "Missing $pack_mcmeta" >&2
  exit 1
fi

python3 - "$gradle_properties" "$mc_version" "$pack_format" "$plugin_version" <<'PY'
import re
import sys

path, mc, pack_format, plugin_version = sys.argv[1:5]

with open(path, "r", encoding="utf-8") as fh:
    data = fh.read()

def upsert(key: str, value: str, text: str) -> str:
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.M)
    if pattern.search(text):
        return pattern.sub(f"{key}={value}", text)
    return text.rstrip("\n") + f"\n{key}={value}\n"

data = upsert("minecraftVersion", mc, data)
data = upsert("resourcepackPackFormat", pack_format, data)
if plugin_version:
    data = upsert("pluginVersion", plugin_version, data)

with open(path, "w", encoding="utf-8") as fh:
    fh.write(data)
PY

python3 - "$pack_mcmeta" "$pack_format" <<'PY'
import re
import sys

path, pack_format = sys.argv[1:3]
with open(path, "r", encoding="utf-8") as fh:
    data = fh.read()

pattern = re.compile(r'"pack_format"\s*:\s*\d+')
if not pattern.search(data):
    raise SystemExit("pack_format not found in pack.mcmeta")

updated = pattern.sub(f'"pack_format": {pack_format}', data)

with open(path, "w", encoding="utf-8") as fh:
    fh.write(updated)
PY

if [[ -f "$readme" ]]; then
  python3 - "$readme" "$mc_version" <<'PY'
import re
import sys

path, mc = sys.argv[1:3]
with open(path, "r", encoding="utf-8") as fh:
    data = fh.read()

pattern = re.compile(r"\*\*Supported server version: [^*]+\*\*")
replacement = f"**Supported server version: {mc}+**"
updated = pattern.sub(replacement, data, count=1)

with open(path, "w", encoding="utf-8") as fh:
    fh.write(updated)
PY
fi

if [[ "$skip_paper_check" -eq 0 ]]; then
  if [[ ! -f "$paper_jar" ]]; then
    echo "Missing $paper_jar (place your server jar at repo root or use --skip-paper-check)" >&2
    exit 1
  fi
  python3 - "$paper_jar" "$mc_version" <<'PY'
import json
import re
import sys
import zipfile

jar_path, expected = sys.argv[1:3]
candidates = [
    "META-INF/maven/io.papermc.paper/paper-server/pom.properties",
    "META-INF/maven/io.papermc.paper/paper-api/pom.properties",
    "META-INF/maven/io.papermc.paper/paperclip/pom.properties",
    "version.json",
]

version_value = None
with zipfile.ZipFile(jar_path) as zf:
    names = set(zf.namelist())
    for name in candidates:
        if name not in names:
            continue
        raw = zf.read(name).decode("utf-8", errors="ignore")
        if name.endswith("pom.properties"):
            for line in raw.splitlines():
                if line.startswith("version="):
                    version_value = line.split("=", 1)[1].strip()
                    break
        elif name == "version.json":
            try:
                payload = json.loads(raw)
                version_value = payload.get("id") or payload.get("name")
            except json.JSONDecodeError:
                pass
        if version_value:
            break

if not version_value:
    print("Warning: could not determine Paper MC version from paper.jar", file=sys.stderr)
    raise SystemExit(0)

match = re.search(r"\d+\.\d+(?:\.\d+)?", version_value)
if not match:
    print(f"Warning: could not parse MC version from '{version_value}'", file=sys.stderr)
    raise SystemExit(0)

found = match.group(0)
if found != expected:
    raise SystemExit(f"paper.jar MC version is {found}, expected {expected}")
PY
fi

echo "Running shader sync + validation..."
"$ROOT_DIR/gradlew" syncShaderResources validateResourcepack

echo "Updated minecraftVersion=$mc_version packFormat=$pack_format"
