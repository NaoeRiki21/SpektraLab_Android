#!/usr/bin/env bash
# stage-android-assets.sh -- prepare engine deps, compile Vulkan SPIR-V kernels,
# and stage all baked profiles (color + B&W product profiles) into Android assets.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/.." && pwd)"
assets_dir="$repo/android/app/src/main/assets/engine"

# 1. Ensure pinned dependencies (LibRaw 0.22.2 + Vulkan-Headers) are fetched
"$here/setup-deps.sh" "$repo/build/deps" >/dev/null

# 2. Clean and copy base engine resources
rm -rf "$assets_dir"
mkdir -p "$assets_dir/profiles" "$assets_dir/vulkan" "$assets_dir/fixtures" "$assets_dir/io"

cp -R "$here/resources/." "$assets_dir/"

# 3. Copy product profiles (CineStill 800T + 4 B&W films + Ilford Multigrade IV RC paper)
if [[ -d "$here/resources_product/profiles" ]]; then
  cp "$here/resources_product/profiles/"*.json "$assets_dir/profiles/"
  cp "$here/resources_product/NOTICE.md" "$assets_dir/PRODUCT_PROFILES_NOTICE.md"
fi

# 4. Merge resources_product/neutral_print_filters.json into neutral_print_filters.json
if [[ -f "$here/resources_product/neutral_print_filters.json" ]]; then
  python3 - "$assets_dir/neutral_print_filters.json" "$here/resources_product/neutral_print_filters.json" <<'PY'
import json, sys
base_path, prod_path = sys.argv[1], sys.argv[2]
with open(base_path, "r", encoding="utf-8") as f:
    base = json.load(f)
with open(prod_path, "r", encoding="utf-8") as f:
    prod = json.load(f)
base.update(prod)
with open(base_path, "w", encoding="utf-8") as f:
    json.dump(base, f, indent=1)
PY
fi

# 5. Compile all 34 Vulkan compute shaders (.comp -> .spv)
glslang="$(command -v glslangValidator || command -v glslang || true)"
if [[ -z "$glslang" ]]; then
  echo "error: glslangValidator not found in PATH" >&2
  exit 1
fi

for comp in "$here/src/shaders/vulkan/"*.comp; do
  name="$(basename "$comp" .comp)"
  "$glslang" -V --target-env vulkan1.0 \
    -I"$here/src/shaders/vulkan" \
    -o "$assets_dir/vulkan/${name}.spv" "$comp" >/dev/null
done

# 6. Copy C ABI parity probe fixtures for on-device macOS parity verification
cp "$here/tests/fixtures/windows/abi_probe.f32" "$assets_dir/fixtures/abi_probe.f32"
cp "$here/tests/fixtures/windows/abi_probe_params.json" "$assets_dir/fixtures/abi_probe_params.json"

# 7. Write asset_manifest.txt for fast O(1) extraction on Android startup
(
  cd "$assets_dir"
  find . -type f ! -name "asset_manifest.txt" | sed 's|^\./||' | sort > asset_manifest.txt
)

count="$(wc -l < "$assets_dir/asset_manifest.txt" | tr -d ' ')"
echo "staged $count engine files into $assets_dir" >&2
