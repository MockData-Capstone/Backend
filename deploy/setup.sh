#!/usr/bin/env bash
# EC2(Ubuntu 24.04)에서 처음 한 번만 실행한다: Docker 설치, 스왑 2GB 추가.
# 사용: bash deploy/setup.sh  (Backend 폴더에서)
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
	echo "==> Docker 설치"
	curl -fsSL https://get.docker.com | sudo sh
	sudo usermod -aG docker "$USER"
fi

# 메모리 2GB(t3.small)에서 서버를 돌리면서 이미지를 빌드해도 모자라지 않게 스왑을 둔다.
if ! swapon --show | grep -q /swapfile; then
	echo "==> 스왑 2GB 추가"
	sudo fallocate -l 2G /swapfile
	sudo chmod 600 /swapfile
	sudo mkswap /swapfile
	sudo swapon /swapfile
	echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo
echo "완료. 다음 순서:"
echo "  1) 로그아웃 후 다시 접속한다 (docker 그룹 적용)"
echo "  2) cp deploy/.env.example deploy/.env && nano deploy/.env"
echo "  3) bash deploy/deploy.sh develop"
