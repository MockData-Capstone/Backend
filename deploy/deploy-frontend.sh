#!/usr/bin/env bash
# 프론트(Frontend 저장소 develop)를 빌드해 caddy가 서비스하는 ~/frontend/current로 바꿔 끼운다.
# 사용: bash deploy/deploy-frontend.sh          develop 최신 커밋을 바로 배포
#       bash deploy/deploy-frontend.sh --auto   새 커밋이 있고 GitHub Actions CI가 통과했을 때만 배포
#                                                (zipfit-frontend-deploy.timer가 2분마다 실행한다)
# 빌드가 실패하면 current를 바꾸지 않으므로 기존 화면이 그대로 남는다.
set -euo pipefail

REPO=https://github.com/MockData-Capstone/Frontend.git
API=https://api.github.com/repos/MockData-Capstone/Frontend
BRANCH=develop
SRC="$HOME/Frontend"            # 소스 (git)
OUT="$HOME/frontend"            # releases/<커밋>, current -> releases/<커밋>
ENV_FILE="$(cd "$(dirname "$0")" && pwd)/.env"
AUTO=0
[ "${1:-}" = "--auto" ] && AUTO=1

mkdir -p "$OUT/releases"
# 타이머와 직접 실행이 겹치지 않게 한 번에 하나만 돈다.
exec 9>"$OUT/.lock"
if ! flock -n 9; then
	echo "다른 프론트 배포가 진행 중이라 건너뛴다."
	exit 0
fi

sha="$(git ls-remote "$REPO" "refs/heads/$BRANCH" | cut -f1)"
[ -n "$sha" ] || { echo "$BRANCH 커밋을 찾지 못했다." >&2; exit 1; }
current="$(basename "$(readlink "$OUT/current" 2>/dev/null)" 2>/dev/null || true)"

if [ "$AUTO" = 1 ]; then
	[ "$sha" = "$current" ] && exit 0
	[ "$sha" = "$(cat "$OUT/.failed" 2>/dev/null || true)" ] && exit 0
	# 그 커밋의 GitHub Actions 결과 (check runs): 모두 끝나고 모두 성공해야 배포한다.
	ci="$(curl -fsS -H 'Accept: application/vnd.github+json' "$API/commits/$sha/check-runs" | python3 -c '
import json, sys
runs = json.load(sys.stdin).get("check_runs", [])
if not runs or any(r["status"] != "completed" for r in runs):
    print("pending")
elif all(r["conclusion"] in ("success", "skipped", "neutral") for r in runs):
    print("success")
else:
    print("failure")
')"
	case "$ci" in
		success) ;;
		pending) echo "${sha:0:7}: CI 진행 중이라 다음에 다시 확인한다."; exit 0 ;;
		*) echo "${sha:0:7}: CI 실패라 배포하지 않는다."; echo "$sha" > "$OUT/.failed"; exit 0 ;;
	esac
fi

echo "==> 프론트 ${sha:0:7} 배포 (지금: ${current:0:7})"
[ -d "$SRC/.git" ] || git clone -q "$REPO" "$SRC"
git -C "$SRC" fetch -q origin "$BRANCH"
git -C "$SRC" checkout -q --force "$sha"

# API·WebSocket은 같은 주소로 보낸다 (VITE_API_BASE_URL을 비우면 프론트가 자기 주소를 쓴다).
# 카카오 지도 JavaScript 키는 deploy/.env의 VITE_KAKAO_JS_KEY (없으면 지도 자리에 안내 문구가 나온다).
kakao="$(grep -E '^VITE_KAKAO_JS_KEY=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)"
printf 'VITE_API_BASE_URL=\nVITE_KAKAO_JS_KEY=%s\n' "$kakao" > "$SRC/.env.production.local"

# 서버에 Node를 설치하지 않고 컨테이너로 빌드한다 (파일 소유자는 ubuntu 그대로).
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$SRC":/app -w /app node:24 \
	sh -c 'npm ci --no-audit --no-fund && npm run build'

rel="$OUT/releases/$sha"
rm -rf "$rel"
cp -r "$SRC/dist" "$rel"
# current를 한 번에 바꾼다 (상대 경로라 caddy 컨테이너 안에서도 같은 곳을 가리킨다).
ln -sfn "releases/$sha" "$OUT/current.tmp"
mv -T "$OUT/current.tmp" "$OUT/current"
rm -f "$OUT/.failed"
# 최근 3개만 남긴다.
ls -1dt "$OUT"/releases/* | tail -n +4 | xargs -r rm -rf
echo "==> 배포 완료: ${sha:0:7}"
