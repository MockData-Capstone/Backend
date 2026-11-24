#!/usr/bin/env bash
# 백엔드 자동 배포 타이머를 설치한다 (처음 한 번, 서버에서 ubuntu 사용자로 실행).
# 2분마다 deploy.sh --auto를 돌려, GitHub Actions가 테스트를 통과한 새 이미지를 올렸으면 받아서 배포한다.
# 로그: journalctl -u zipfit-backend-deploy -f
# 잠깐 끄기(시연 중): sudo systemctl stop zipfit-backend-deploy.timer    다시 켜기: sudo systemctl start zipfit-backend-deploy.timer
# 완전히 끄기: sudo systemctl disable --now zipfit-backend-deploy.timer
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")" && pwd)/deploy.sh"
USER_NAME="$(id -un)"

sudo tee /etc/systemd/system/zipfit-backend-deploy.service >/dev/null <<EOF
[Unit]
Description=zipfit backend: deploy the latest image that passed CI
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
User=$USER_NAME
Environment=HOME=/home/$USER_NAME
ExecStart=/usr/bin/bash $SCRIPT --auto
TimeoutStartSec=10min
EOF

sudo tee /etc/systemd/system/zipfit-backend-deploy.timer >/dev/null <<'EOF'
[Unit]
Description=zipfit backend: check for a new image every 2 minutes

[Timer]
OnBootSec=1min
OnUnitActiveSec=2min
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now zipfit-backend-deploy.timer
systemctl list-timers zipfit-backend-deploy.timer --no-pager
