#!/usr/bin/env bash
# 서버에서 브랜치의 최신 코드로 다시 배포한다.
# 사용: bash deploy/deploy.sh [브랜치]   (기본값 main)
set -euo pipefail
cd "$(dirname "$0")"

BRANCH="${1:-main}"
if [ ! -f .env ]; then
	echo "deploy/.env가 없다. .env.example을 복사해서 채운다." >&2
	exit 1
fi

echo "==> $BRANCH 최신 코드 받기"
git fetch origin
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

# coturn이 클라이언트에게 알려 줄 공인 IP와 서버 안쪽 사설 IP (EC2는 NAT 뒤에 있다)
export TURN_PUBLIC_IP="$(curl -fsS https://checkip.amazonaws.com)"
export TURN_PRIVATE_IP="$(hostname -I | awk '{print $1}')"

echo "==> 빌드·실행"
docker compose up -d --build
docker image prune -f >/dev/null

docker compose ps
echo
echo "로그 보기: cd deploy && docker compose logs -f app"
