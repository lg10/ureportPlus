#!/usr/bin/env bash
# =============================================================================
# ureport-plus 一键发布脚本（Maven Central / Sonatype Central Portal）
#
# 发布方式：把 parent/core/font/console/all 五个模块的构件合并成单个
# bundle（ureportplus-<version>-maven-central.zip）一次性上传，
# 与历史版本（1.0.0~1.0.4）的发布方式保持一致。
#
# 用法：
#   ./release.sh <新版本号> [--auto] [--no-upload] [--skip-build]
#
#   <新版本号>    目标版本，如 1.0.6。传 same 则不改版本号直接重新打包
#   --auto       上传后自动发布（默认 USER_MANAGED，需在网页上点 Publish）
#   --no-upload  只构建合并包，不上传
#   --skip-build 跳过改版本和构建，直接用已有的各模块 bundle 合并上传
#
# 前置条件：
#   - ~/.m2/settings.xml 中配置了 <server id="central"> 的 Portal 令牌
#   - ~/.m2/settings.xml 的 gpg profile 配置了 gpg.keyname
#   - 本机已安装 gpg、jar、zip、unzip、python3
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CENTRAL_PLUGIN="org.sonatype.central:central-publishing-maven-plugin:0.8.0:publish"
MODULES=(ureportplus-parent ureportplus-core ureportplus-font ureportplus-console ureportplus-all)
POMS=(
  ureportplus-parent/pom.xml
  ureportplus-core/pom.xml
  ureportplus-font/pom.xml
  ureportplus-console/pom.xml
  ureportplus-all/pom.xml
)

AUTO_PUBLISH=false
NO_UPLOAD=false
SKIP_BUILD=false
VERSION=""

for arg in "$@"; do
  case "$arg" in
    --auto) AUTO_PUBLISH=true ;;
    --no-upload) NO_UPLOAD=true ;;
    --skip-build) SKIP_BUILD=true ;;
    *) VERSION="$arg" ;;
  esac
done

log()  { echo "[$(date +%H:%M:%S)] $*"; }
fail() { echo "[ERROR] $*" >&2; exit 1; }

# 直接以 Java 主类方式启动 Maven：新版 mvn 启动脚本会向 JVM 注入
# --enable-native-access 等 JDK 9+ 参数，在 JDK 8 下无法启动
run_mvn() {
  local mvn_home mvn_jar java_bin
  mvn_home="$(dirname "$(dirname "$(readlink -f "$(command -v mvn)")")")"
  mvn_jar="$(ls "${mvn_home}/boot/"plexus-classworlds-*.jar | head -1)"
  [ -n "$mvn_jar" ] || fail "找不到 ${mvn_home}/boot/plexus-classworlds-*.jar"
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    java_bin="${JAVA_HOME}/bin/java"
  else
    java_bin="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)/bin/java"
    [ -x "$java_bin" ] || java_bin="java"
  fi
  "$java_bin" ${MAVEN_OPTS:-} \
    -classpath "$mvn_jar" \
    "-Dclassworlds.conf=${mvn_home}/bin/m2.conf" \
    "-Dmaven.home=${mvn_home}" \
    "-Dlibrary.jansi.path=${mvn_home}/lib/jansi-native" \
    "-Dmaven.multiModuleProjectDirectory=${MAVEN_BASEDIR:-$PWD}" \
    org.codehaus.plexus.classworlds.launcher.Launcher "$@"
}

# ---------- 读取当前版本 ----------
# 取 ureportplus-parent 自身坐标紧随的 <version>；不能取文件中第一个数字版本，
# 否则会误取插件版本（如 central-publishing-maven-plugin）
CURRENT_VERSION=$(grep -A1 '<artifactId>ureportplus-parent</artifactId>' "$SCRIPT_DIR/ureportplus-parent/pom.xml" 2>/dev/null \
  | grep -m1 -oE '<version>[^<]+</version>' | sed -E 's/<\/?version>//g' || true)
printf '%s' "${CURRENT_VERSION:-}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || fail "parent pom 的项目版本 '${CURRENT_VERSION:-<空>}' 不是合法的 x.y.z 数字，pom 可能已损坏，请先用 git 恢复后重试"
[ -n "$VERSION" ] || fail "缺少版本号参数。当前版本：${CURRENT_VERSION}。用法：./release.sh <新版本号> [--auto] [--no-upload] [--skip-build]"
if [ "$VERSION" != "same" ]; then
  printf '%s' "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$' \
    || fail "非法版本号：'$VERSION'（必须是 x.y.z 数字形式；--auto/--no-upload/--skip-build 是选项参数，不是版本号）"
fi

if [ "$VERSION" = "same" ]; then
  VERSION="$CURRENT_VERSION"
  log "保持版本号不变：$VERSION"
elif [ "$VERSION" != "$CURRENT_VERSION" ]; then
  log "版本变更：$CURRENT_VERSION -> $VERSION"
else
  log "版本号未变化：$VERSION"
fi

# ---------- 从 settings.xml 读取凭据 ----------
SETTINGS="$HOME/.m2/settings.xml"
[ -f "$SETTINGS" ] || fail "找不到 $SETTINGS"
CRED_LINE=$(SETTINGS_FILE="$SETTINGS" python3 <<'EOF'
import re, os
s = open(os.environ['SETTINGS_FILE']).read()
servers = s.split('</servers>')[0]
m = re.search(r'<server>\s*<id>central</id>\s*<username>(.*?)</username>\s*<password>(.*?)</password>', servers, re.S)
if not m: raise SystemExit('settings.xml 缺少 <server id="central"> 凭据（Central Portal 令牌）')
g = re.search(r'<gpg\.keyname>(.*?)</gpg\.keyname>', s)
print(m.group(1), m.group(2), g.group(1) if g else 'DEFAULT')
EOF
) || fail "解析 settings.xml 失败"
read -r CENTRAL_USER CENTRAL_PASS GPG_KEY <<< "$CRED_LINE"
log "Portal 账号：${CENTRAL_USER}；GPG key：${GPG_KEY}"

# ---------- 第一步：修改版本号 ----------
if ! $SKIP_BUILD && [ "$VERSION" != "$CURRENT_VERSION" ]; then
  log "修改 ${#POMS[@]} 个 pom 中的版本号..."
  for pom in "${POMS[@]}"; do
    sed -i '' -e "s|<version>${CURRENT_VERSION}</version>|<version>${VERSION}</version>|g" \
              -e "s|<tag>v${CURRENT_VERSION}</tag>|<tag>v${VERSION}</tag>|g" \
              "$SCRIPT_DIR/$pom"
  done
  REMAIN=$(grep -rl "<version>${CURRENT_VERSION}</version>" "${POMS[@]/#/$SCRIPT_DIR/}" 2>/dev/null || true)
  [ -z "$REMAIN" ] || fail "以下文件仍残留旧版本号：$REMAIN"
  VER_PROPS="$SCRIPT_DIR/ureportplus-core/src/main/resources/ureportplus-version.properties"
  if [ -f "$VER_PROPS" ]; then
    sed -i '' -e "s|^ureportplus.version=.*|ureportplus.version=${VERSION}|" "$VER_PROPS"
  fi
  log "版本号修改完成"

  log "同步 README 文档中的版本号..."
  for doc in README.md README-zh_CN.md; do
    [ -f "$SCRIPT_DIR/$doc" ] || continue
    DOC_FILE="$SCRIPT_DIR/$doc" NEW_VER="$VERSION" REL_DATE="$(date +%F)" python3 <<'EOF' || fail "$doc 版本号同步失败"
import re, os
path, ver, day = os.environ['DOC_FILE'], os.environ['NEW_VER'], os.environ['REL_DATE']
s = open(path, encoding='utf-8').read()
s, n1 = re.subn(r'(<artifactId>ureportplus-(?:console|all)</artifactId>\s*<version>)[^<]+(</version>)',
                lambda m: m.group(1) + ver + m.group(2), s)
s, n2 = re.subn(r'(com\.kingint\.ureportplus:ureportplus-console:)[0-9][\w.\-]*',
                lambda m: m.group(1) + ver, s)
s, n3 = re.subn(r'((?:Latest release:|最新版本：)\s*\*\*v)[^*]+(\*\*\s*\()[0-9-]+(\))',
                lambda m: m.group(1) + ver + m.group(2) + day + m.group(3), s)
if n1 < 2 or n2 < 1 or n3 < 1:
    raise SystemExit(f'替换数量异常（maven={n1}, gradle={n2}, latest={n3}），文档结构可能已变化，请人工检查')
open(path, 'w', encoding='utf-8').write(s)
EOF
  done
  DOC_REMAIN=$(grep -l "<version>${CURRENT_VERSION}</version>" "$SCRIPT_DIR/README.md" "$SCRIPT_DIR/README-zh_CN.md" 2>/dev/null || true)
  [ -z "$DOC_REMAIN" ] || fail "以下文档仍残留旧版本号：$DOC_REMAIN"
  log "README 版本号同步完成"
fi

# ---------- 第二步：逐模块构建并生成本地 bundle（不上传） ----------
WORK="$(mktemp -d)/bundle"
mkdir -p "$WORK"

if ! $SKIP_BUILD; then
  for m in "${MODULES[@]}"; do
    log "构建 $m ..."
    ( cd "$SCRIPT_DIR/$m" && \
      run_mvn -q install "$CENTRAL_PLUGIN" -DskipTests \
          -DpublishingType=USER_MANAGED -DskipPublishing=true ) \
      || fail "$m 构建失败"
    [ -f "$SCRIPT_DIR/$m/target/central-publishing/central-bundle.zip" ] || fail "$m 未生成 bundle"
    unzip -q -o "$SCRIPT_DIR/$m/target/central-publishing/central-bundle.zip" -d "$WORK"
    log "$m 完成"
  done
else
  log "跳过构建，使用各模块已有的 bundle..."
  for m in "${MODULES[@]}"; do
    b="$SCRIPT_DIR/$m/target/central-publishing/central-bundle.zip"
    [ -f "$b" ] || fail "$b 不存在，请先构建或去掉 --skip-build"
    unzip -q -o "$b" -d "$WORK"
  done
fi

# ---------- 第三步：ureportplus-all 无源码，补一个占位 javadoc jar ----------
ALL_DIR="$WORK/com/kingint/ureportplus/ureportplus-all/$VERSION"
if [ ! -f "$ALL_DIR/ureportplus-all-${VERSION}-javadoc.jar" ]; then
  log "为 ureportplus-all 生成占位 javadoc jar..."
  JD="$(mktemp -d)"
  echo "UReportPlus All-in-One aggregator module - no public API." > "$JD/README.txt"
  jar cf "$ALL_DIR/ureportplus-all-${VERSION}-javadoc.jar" -C "$JD" .
  rm -rf "$JD"
  f="$ALL_DIR/ureportplus-all-${VERSION}-javadoc.jar"
  md5 -q "$f" > "$f.md5"
  shasum -a 1   "$f" | awk '{print $1}' > "$f.sha1"
  shasum -a 256 "$f" | awk '{print $1}' > "$f.sha256"
  shasum -a 512 "$f" | awk '{print $1}' > "$f.sha512"
  if [ "$GPG_KEY" = "DEFAULT" ]; then
    gpg --batch --yes --output "$f.asc" --detach-sign "$f"
  else
    gpg --batch --yes -u "$GPG_KEY" --output "$f.asc" --detach-sign "$f"
  fi
fi

# ---------- 第四步：合并打包 ----------
BUNDLE="$SCRIPT_DIR/ureportplus-${VERSION}-maven-central.zip"
rm -f "$BUNDLE"
( cd "$WORK" && zip -q -r "$BUNDLE" . )
ASCS=$(unzip -l "$BUNDLE" | grep -c '\.asc$' || true)
BUNDLE_SIZE=$(du -h "$BUNDLE" | cut -f1)
log "合并包已生成：${BUNDLE}（${BUNDLE_SIZE}，${ASCS} 个签名文件）"
[ "$ASCS" -ge 17 ] || fail "签名文件数量不足（$ASCS < 17），请检查各模块 gpg 签名"

if $NO_UPLOAD; then
  log "--no-upload 已指定，跳过上传"
  exit 0
fi

# ---------- 第五步：上传到 Central Portal ----------
PUBLISH_TYPE="USER_MANAGED"
$AUTO_PUBLISH && PUBLISH_TYPE="AUTOMATIC"

log "上传到 Central Portal（publishingType=${PUBLISH_TYPE}）..."
HTTP_CODE=$(curl -s -o /tmp/central-upload-resp.txt -w '%{http_code}' \
  -u "$CENTRAL_USER:$CENTRAL_PASS" \
  -F "bundle=@$BUNDLE" \
  -F "name=ureportplus-${VERSION}-maven-central.zip" \
  -F "publishingType=$PUBLISH_TYPE" \
  'https://central.sonatype.com/api/v1/publisher/upload')

if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "200" ]; then
  cat /tmp/central-upload-resp.txt 2>/dev/null || true
  fail "上传失败（HTTP ${HTTP_CODE}）。若提示组件被其它部署占用，请先在网页上 Drop 旧部署或等其结束后重试：./release.sh same --skip-build [--auto]"
fi

DEPLOY_ID=$(cat /tmp/central-upload-resp.txt | tr -d '"')
log "上传成功！deploymentId: $DEPLOY_ID"

# ---------- 第六步：轮询校验状态 ----------
log "等待校验结果..."
for i in $(seq 1 60); do
  STATE=$(curl -s -u "$CENTRAL_USER:$CENTRAL_PASS" \
    'https://central.sonatype.com/api/v1/publisher/deployments?page=0&size=10' \
    | DEPLOY_ID="$DEPLOY_ID" python3 -c '
import json,sys,os
d=json.load(sys.stdin)["deployments"]
t=[x for x in d if x["deploymentId"]==os.environ["DEPLOY_ID"]]
if not t: sys.exit("MISSING")
x=t[0]
print(x["deploymentState"])
for c in x.get("deploymentComponents") or []:
    for e in c.get("errors") or []: print("ERR "+c["name"]+": "+e)
'
  )
  STATE_FIRST=$(printf '%s\n' "$STATE" | head -1)
  log "状态：${STATE_FIRST}"
  case "$STATE" in
    VALIDATED*|PUBLISHING*|PUBLISHED*) log "校验通过！${STATE_FIRST}"; break ;;
    FAILED*) printf '%s\n' "$STATE"; fail "校验失败，详见上方错误；修复后重试：./release.sh same --skip-build [--auto]" ;;
    MISSING*) fail "未找到该部署（可能已被丢弃），请到网页确认：https://central.sonatype.com/publishing/deployments" ;;
  esac
  sleep 20
done

log "完成。发布详情：https://central.sonatype.com/publishing/deployments"
$AUTO_PUBLISH || log "提示：当前为手动发布模式，请到网页上点击 Publish。"
