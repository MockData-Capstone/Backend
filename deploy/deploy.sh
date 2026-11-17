#!/usr/bin/env bash
# 서버에서 브랜치의 최신 코드로 다시 배포한다.
# 사용: bash deploy/deploy.sh [브랜치]   (기본값 main)
#
# 전체를 main 함수로 감싼다: 실행 도중 git pull이 이 파일을 바꿔도 bash는 이미 다 읽은 내용으로 끝까지 돈다.
set -euo pipefail

main() {
	cd "$(dirname "$0")"

	local branch="${1:-main}"
	if [ ! -f .env ]; then
		echo "deploy/.env가 없다. .env.example을 복사해서 채운다." >&2
		exit 1
	fi

	echo "==> $branch 최신 코드 받기"
	git fetch origin
	git checkout "$branch"
	git pull --ff-only origin "$branch"

	# coturn이 클라이언트에게 알려 줄 공인 IP와 서버 안쪽 사설 IP (EC2는 NAT 뒤에 있다).
	# .env에 적어 두어, 서버에서 docker compose를 직접 실행해도 같은 값을 쓴다.
	setenv TURN_PUBLIC_IP "$(curl -fsS https://checkip.amazonaws.com)"
	setenv TURN_PRIVATE_IP "$(hostname -I | awk '{print $1}')"

	echo "==> 빌드"
	docker compose build app

	# compose는 새 이미지를 빌드해도 실행 중인 app 컨테이너를 바꾸지 않을 때가 있어(v5.7.1에서 확인),
	# app은 매번 새 이미지로 다시 띄운다 (15~20초 동안 API가 멈춘다). coturn은 설정이 바뀔 때만 다시 띄운다.
	echo "==> 실행"
	docker compose up -d --force-recreate --no-deps app
	docker compose up -d

	# Caddyfile은 파일 하나를 연결해서, 내용이 바뀌어도 compose가 알아채지 못한다. 바뀌었을 때만 caddy를 다시 띄운다.
	local sum
	sum="$(sha256sum Caddyfile | cut -d' ' -f1)"
	if [ "$sum" != "$(cat "$HOME/.zipfit-caddyfile.sha256" 2>/dev/null || true)" ]; then
		echo "==> Caddyfile이 바뀌어 caddy를 다시 띄운다"
		docker compose up -d --force-recreate --no-deps caddy
		echo "$sum" > "$HOME/.zipfit-caddyfile.sha256"
	fi
	docker image prune -f >/dev/null

	docker compose ps
	echo
	echo "로그 보기: cd deploy && docker compose logs -f app"
}

# .env의 KEY=값을 바꾸거나 없으면 추가한다 (값은 IP처럼 단순한 문자열만 넣는다).
setenv() {
	if grep -q "^$1=" .env; then
		sed -i "s|^$1=.*|$1=$2|" .env
	else
		printf '%s=%s\n' "$1" "$2" >> .env
	fi
}

main "$@"
exit
