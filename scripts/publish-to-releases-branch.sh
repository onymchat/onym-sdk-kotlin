#!/bin/sh
# Stage Gradle's Maven publication output into the long-lived
# `releases` branch (a static Maven repo served by GitHub raw-content),
# regenerate maven-metadata.xml across all published versions, and
# push.
#
# Usage:
#   ./scripts/publish-to-releases-branch.sh \
#       --version 0.0.1 \
#       --repo onymchat/onym-sdk-kotlin \
#       [--branch releases] \
#       [--maven-local build/maven-local]
#
# Consumer-side resolution:
#   repositories {
#       maven { url = "https://raw.githubusercontent.com/<repo>/<branch>/" }
#   }
#   dependencies {
#       implementation("chat.onym:onym-sdk:0.0.1")
#   }
#
# Prereq: `./gradlew publishAarPublicationToReleaseRepository
#         -PreleaseRepoUrl=file:///abs/path/to/build/maven-local`
#         has run and produced
#         build/maven-local/chat/onym/onym-sdk/<version>/{onym-sdk-<version>.aar,
#                                                          onym-sdk-<version>.pom,
#                                                          onym-sdk-<version>.module,
#                                                          *.md5, *.sha1, *.sha256, *.sha512}.

set -eu

VERSION=""
REPO=""
BRANCH="releases"
MAVEN_LOCAL=""

while [ $# -gt 0 ]; do
    case "$1" in
        --version)     VERSION="$2"; shift 2 ;;
        --repo)        REPO="$2"; shift 2 ;;
        --branch)      BRANCH="$2"; shift 2 ;;
        --maven-local) MAVEN_LOCAL="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
    esac
done

[ -n "$VERSION" ] || { echo "--version required (e.g. 0.0.1)" >&2; exit 1; }
[ -n "$REPO" ]    || { echo "--repo required (OWNER/REPO)" >&2; exit 1; }

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
[ -z "$MAVEN_LOCAL" ] && MAVEN_LOCAL="$REPO_ROOT/build/maven-local"

GROUP_PATH="chat/onym"
ARTIFACT="onym-sdk"
SRC_VERSION_DIR="$MAVEN_LOCAL/$GROUP_PATH/$ARTIFACT/$VERSION"
[ -d "$SRC_VERSION_DIR" ] || {
    echo "expected Maven publication at $SRC_VERSION_DIR" >&2
    echo "(run: ./gradlew publishAarPublicationToReleaseRepository -PreleaseRepoUrl=file://$MAVEN_LOCAL)" >&2
    exit 1
}

# ─── Worktree the releases branch (or create it as an orphan) ────
SCRATCH="$REPO_ROOT/build/releases-staging"
git -C "$REPO_ROOT" worktree remove --force "$SCRATCH" 2>/dev/null || true
rm -rf "$SCRATCH"

if git -C "$REPO_ROOT" ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    echo "==> Worktree-checking out existing origin/$BRANCH into $SCRATCH"
    git -C "$REPO_ROOT" fetch origin "$BRANCH:refs/remotes/origin/$BRANCH"
    git -C "$REPO_ROOT" worktree add -B "$BRANCH" "$SCRATCH" "origin/$BRANCH"
else
    echo "==> Initializing fresh orphan $BRANCH branch in $SCRATCH"
    git -C "$REPO_ROOT" worktree add --detach "$SCRATCH" HEAD
    ( cd "$SCRATCH" && git switch --orphan "$BRANCH" && git rm -rf . >/dev/null 2>&1 || true )
    # Drop a README so the branch isn't completely empty on first publish.
    cat > "$SCRATCH/README.md" <<EOF
# OnymSDK Kotlin — Maven repository

This branch is a **static Maven repository** for OnymSDK Kotlin
releases. Do not commit here directly — \`scripts/publish-to-releases-branch.sh\`
in the \`main\` branch overwrites everything here on every release.

## Consume from a Gradle project

\`\`\`kotlin
repositories {
    maven { url = uri("https://raw.githubusercontent.com/$REPO/$BRANCH/") }
}
dependencies {
    implementation("chat.onym:onym-sdk:0.0.1")
}
\`\`\`

Released versions are listed in \`$GROUP_PATH/$ARTIFACT/maven-metadata.xml\`.
EOF
fi

# ─── Stage the new version (overlay; safe to re-run for same tag) ──
DST_VERSION_DIR="$SCRATCH/$GROUP_PATH/$ARTIFACT/$VERSION"
rm -rf "$DST_VERSION_DIR"
mkdir -p "$DST_VERSION_DIR"
cp "$SRC_VERSION_DIR"/* "$DST_VERSION_DIR/"

# ─── Regenerate maven-metadata.xml across every version on disk ───
META="$SCRATCH/$GROUP_PATH/$ARTIFACT/maven-metadata.xml"
echo "==> Regenerating $META"

# List version dirs (X.Y.Z only — skip stray files like maven-metadata.xml).
# `sort -V` orders them as semantic versions (1.10.0 > 1.9.0).
VERSIONS="$(
    cd "$SCRATCH/$GROUP_PATH/$ARTIFACT" && \
    ls -1 | while read -r entry; do
        [ -d "$entry" ] || continue
        case "$entry" in
            [0-9]*.[0-9]*.[0-9]*) echo "$entry" ;;
        esac
    done | sort -V
)"
LATEST="$(echo "$VERSIONS" | tail -n1)"
NOW="$(date -u +%Y%m%d%H%M%S)"

{
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<metadata>\n'
    printf '  <groupId>chat.onym</groupId>\n'
    printf '  <artifactId>%s</artifactId>\n' "$ARTIFACT"
    printf '  <versioning>\n'
    printf '    <latest>%s</latest>\n'  "$LATEST"
    printf '    <release>%s</release>\n' "$LATEST"
    printf '    <versions>\n'
    echo "$VERSIONS" | while read -r v; do
        [ -z "$v" ] && continue
        printf '      <version>%s</version>\n' "$v"
    done
    printf '    </versions>\n'
    printf '    <lastUpdated>%s</lastUpdated>\n' "$NOW"
    printf '  </versioning>\n'
    printf '</metadata>\n'
} > "$META"

# Sidecar checksums for maven-metadata.xml (consumers verify them).
# `md5` (BSD/macOS) vs `md5sum` (GNU/Linux) — handle both.
checksum_to() {
    file="$1"; algo="$2"; out="$3"
    case "$algo" in
        md5)
            if command -v md5 >/dev/null 2>&1; then
                md5 -q "$file" > "$out"
            else
                md5sum "$file" | cut -d' ' -f1 > "$out"
            fi ;;
        sha1)
            shasum -a 1 "$file" | cut -d' ' -f1 > "$out" ;;
    esac
}
checksum_to "$META" md5  "$META.md5"
checksum_to "$META" sha1 "$META.sha1"

# ─── Commit + push ────────────────────────────────────────────────
(
    cd "$SCRATCH"
    git add .
    if git diff --cached --quiet; then
        echo "no changes to publish (output matches what's already on $BRANCH)"
        exit 0
    fi
    git commit -m "publish chat.onym:$ARTIFACT:$VERSION (Maven static repo)"
    git push -u origin "$BRANCH"
)

git -C "$REPO_ROOT" worktree remove --force "$SCRATCH" 2>/dev/null || true

echo
echo "Published chat.onym:$ARTIFACT:$VERSION"
echo "  https://raw.githubusercontent.com/$REPO/$BRANCH/$GROUP_PATH/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.aar"
