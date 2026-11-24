#!/usr/bin/env bash
# 서버에서 백엔드를 배포한다. 이미지는 GitHub Actions가 테스트를 통과한 커밋으로 만들어 GHCR에 올린다.
# 사용: bash deploy/deploy.sh [브랜치]           브랜치(기본 develop) 코드와 그 브랜치의 최신 이미지로 배포
#       bash deploy/deploy.sh [브랜치] --build   이미지를 받지 않고 서버에서 직접 빌드 (GHCR을 쓸 수 없을 때)
#       bash deploy/deploy.sh --auto             새 이미지가 올라왔을 때만 배포 (zipfit-backend-deploy.timer가 2분마다 실행)
#
# 전체를 main 함수로 감싼다: 실행 도중 git pull이 이 파일을 바꿔도 bash는 이미 다 읽은 내용으로 끝까지 돈다.
set -euo pipefail

main() {
	cd "$(dirname "$0")"

	local branch="" build=0 auto=0
	for arg in "$@"; do
		case "$arg" in
			--build) build=1 ;;
			--auto) auto=1 ;;
			*) branch="$arg" ;;
		esac
	done
	if [ ! -f .env ]; then
		echo "deploy/.env가 없다. .env.example을 복사해서 채운다." >&2
		exit 1
	fi
	# --auto는 마지막으로 배포한 브랜치를 따라간다.
	branch="${branch:-$(grep -E '^APP_TAG=' .env | cut -d= -f2- || true)}"
	branch="${branch:-develop}"

	# 타이머와 직접 실행이 겹치지 않게 한 번에 하나만 돈다.
	exec 9>"$HOME/.zipfit-backend-deploy.lock"
	if ! flock -n 9; then
		echo "다른 백엔드 배포가 진행 중이라 건너뛴다."
		exit 0
	fi

	setenv APP_TAG "$branch"
	if [ "$auto" = 1 ]; then
		docker compose pull -q app
		local latest running
		latest="$(docker image inspect -f '{{.Id}}' "$(image)")"
		running="$(docker inspect -f '{{.Image}}' "$(docker compose ps -q app)" 2>/dev/null || true)"
		[ "$latest" = "$running" ] && exit 0
		echo "==> 새 이미지 $(revision)"
	fi

	echo "==> $branch 최신 코드 받기 (compose·Caddyfile 등 배포 설정)"
	git fetch -q origin
	git checkout -q "$branch"
	git pull -q --ff-only origin "$branch"

	# coturn이 클라이언트에게 알려 줄 공인 IP와 서버 안쪽 사설 IP (EC2는 NAT 뒤에 있다).
	# .env에 적어 두어, 서버에서 docker compose를 직접 실행해도 같은 값을 쓴다.
	setenv TURN_PUBLIC_IP "$(curl -fsS https://checkip.amazonaws.com)"
	setenv TURN_PRIVATE_IP "$(hostname -I | awk '{print $1}')"

	if [ "$build" = 1 ]; then
		echo "==> 서버에서 빌드"
		docker compose build app
	elif [ "$auto" = 0 ]; then
		echo "==> 이미지 받기 ($(image))"
		docker compose pull -q app
	fi

	# compose는 이미지가 바뀌어도 실행 중인 app 컨테이너를 바꾸지 않을 때가 있어(v5.7.1에서 확인),
	# app은 매번 새 이미지로 다시 띄운다 (15~20초 동안 API가 멈춘다). coturn은 설정이 바뀔 때만 다시 띄운다.
	echo "==> 실행 ($(revision))"
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

# docker-compose.yml의 app 이미지 이름과 같아야 한다.
image() {
	echo "ghcr.io/mockdata-capstone/zipfit-backend:$(grep -E '^APP_TAG=' .env | cut -d= -f2-)"
}

# 이미지를 만든 커밋 (GitHub Actions가 붙인 라벨. 서버에서 빌드한 이미지는 local)
revision() {
	local rev
	rev="$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$(image)" 2>/dev/null || true)"
	[ -n "$rev" ] && [ "$rev" != "<no value>" ] && echo "${rev:0:7}" || echo local
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
