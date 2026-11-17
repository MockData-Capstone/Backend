#!/usr/bin/env bash
# 프론트 자동 배포 타이머를 설치한다 (처음 한 번, 서버에서 ubuntu 사용자로 실행).
# 2분마다 deploy-frontend.sh --auto를 돌려, develop에 CI를 통과한 새 커밋이 있으면 배포한다.
# 로그: journalctl -u zipfit-frontend-deploy -f    끄기: sudo systemctl disable --now zipfit-frontend-deploy.timer
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")" && pwd)/deploy-frontend.sh"
USER_NAME="$(id -un)"

sudo tee /etc/systemd/system/zipfit-frontend-deploy.service >/dev/null <<EOF
[Unit]
Description=zipfit frontend: deploy develop when CI passed
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
User=$USER_NAME
Environment=HOME=/home/$USER_NAME
ExecStart=/usr/bin/bash $SCRIPT --auto
TimeoutStartSec=15min
EOF

sudo tee /etc/systemd/system/zipfit-frontend-deploy.timer >/dev/null <<'EOF'
[Unit]
Description=zipfit frontend: check develop every 2 minutes

[Timer]
OnBootSec=1min
OnUnitActiveSec=2min
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now zipfit-frontend-deploy.timer
systemctl list-timers zipfit-frontend-deploy.timer --no-pager
