# AWS EC2 배포

EC2 한 대에서 Docker로 세 가지를 함께 띄우고, 프론트와 백엔드를 **같은 주소** `https://zipfit.duckdns.org` 로 서비스한다.

| 컨테이너 | 역할 |
|---|---|
| `app` | 백엔드 (Spring Boot) |
| `caddy` | HTTPS 인증서 자동 발급·갱신. `/api`·`/ws`·`/uploads`는 백엔드로, 나머지는 프론트 빌드(`~/frontend/current`)로 |
| `coturn` | TURN 서버. 이동통신망처럼 P2P가 막힌 환경에서 WebRTC 영상을 중계한다 |

백엔드와 프론트 모두 `develop`에 CI를 통과한 커밋이 올라오면 서버가 2분 안에 가져가 배포한다 (5번, 6번).

서버는 **아시아 태평양(서울) ap-northeast-2**에 있다 (2026-11-22 시드니에서 옮김).
배포용 DB는 아직 Neon **시드니**(`zipfit-prod`)를 쓴다. Neon에는 서울 리전이 없어 쿼리마다 약 140ms가 더 걸리므로, 나중에 서울 RDS로 옮길 계획이다. 팀 개발용 Neon(싱가포르)은 그대로 쓴다.

---

## 0. 배포용 DB 만들기 (Neon)

1. Neon 콘솔 → **New Project** → Region: **AWS Asia Pacific 2 (Sydney)** → 이름 예: `zipfit-prod`
2. 만들어진 프로젝트의 **Connect**에서 연결 문자열 `postgresql://<user>:<password>@<host>/<db>?sslmode=require...` 를 확인한다.
3. 아래 4단계에서 `deploy/.env`에 이렇게 옮긴다 (풀러 주소 `-pooler`가 붙은 host를 쓴다).
   - `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require&channelBinding=require`
   - `DB_USERNAME=<user>`, `DB_PASSWORD=<password>`

테이블은 서버가 처음 뜰 때 만들어진다.

## 1. EC2 (만들어 둠, 2026-11-22, 서울)

| 리소스 | 값 |
|---|---|
| 인스턴스 | `zipfit-backend` (`i-07050950f9e4c87a2`), t4g.small (ARM, 2GB), Ubuntu 24.04, gp3 20GB 암호화 |
| 탄력적 IP | **`3.38.18.55`** (서버를 껐다 켜도 바뀌지 않는다) |
| 보안 그룹 | `zipfit-backend-sg` — TCP 80·443, TCP·UDP 3478, UDP 49160-49200. **SSH(22)는 열지 않는다** |
| IAM 역할 | `zipfit-backend-role` — `AmazonSSMManagedInstanceCore`만 (Session Manager 접속용) |
| 기타 | IMDSv2만 허용, 종료 보호 켬, CPU 크레딧 standard (추가 요금 없음) |

첫 부팅 때 Docker 설치, 스왑 2GB, 저장소(`~/Backend`) 받기가 자동으로 끝났다 (`deploy/setup.sh`와 같은 일).
서버를 새로 만들 때만 `deploy/setup.sh`를 직접 실행한다.

## 2. 도메인 연결 (DuckDNS, 무료)

HTTPS 인증서를 받으려면 도메인이 필요하다.

1. https://www.duckdns.org 에 GitHub 계정으로 로그인
2. sub domain에 이름(예: `zipfit`)을 넣고 **add domain** → `zipfit.duckdns.org`
3. 그 줄의 **current ip**에 탄력적 IP `3.38.18.55`를 넣고 **update ip**

## 3. 서버 접속 (SSH over Session Manager)

22번 포트를 열지 않고, SSH를 AWS Session Manager 통로로 보낸다. `ssh`·`scp`·VS Code Remote-SSH가 그대로 된다.

**브라우저에서 바로 (설치 없음)**: AWS 콘솔 → EC2 → `zipfit-backend` → **연결 → Session Manager → 연결**.
`ssm-user`로 들어가므로 먼저 `sudo -iu ubuntu` 를 입력한다.

**내 PC에서 `ssh zipfit` (처음 한 번 설정)**

1. AWS CLI 로그인: `aws configure set region ap-northeast-2 --profile zipfit` 후 `aws login --profile zipfit` (12시간마다 자동 갱신, 90일마다 다시 로그인)
2. Session Manager 플러그인 설치 (관리자 권한):
   https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe
3. 내 PC 전용 키를 만들고, 공개키를 서버의 `~/.ssh/authorized_keys`에 추가한다 (이미 접속되는 사람이 브라우저 터미널이나 `ssh zipfit`으로 넣어 준다).
   ```powershell
   ssh-keygen -t ed25519 -f "$HOME\.ssh\zipfit_ed25519" -C "zipfit-backend ($env:USERNAME)"
   Get-Content "$HOME\.ssh\zipfit_ed25519.pub"   # 이 한 줄을 서버에 추가한다
   ```
4. `~/.ssh/config`에 추가한다 (경로는 내 PC에 맞게):
   ```
   Host zipfit
       HostName i-07050950f9e4c87a2
       User ubuntu
       IdentityFile ~/.ssh/zipfit_ed25519
       IdentitiesOnly yes
       ServerAliveInterval 30
       ProxyCommand C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "$env:Path = 'C:\Program Files\Amazon\SessionManagerPlugin\bin;' + $env:Path; & 'C:\Users\<내 계정>\AppData\Local\Programs\Amazon\AWSCLIV2\aws.exe' ssm start-session --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p --region ap-northeast-2 --profile zipfit"
   ```
5. 접속: `ssh zipfit` (VS Code: Remote-SSH → `zipfit`)

팀원이 접속하려면 계정 관리자에게 IAM Identity Center 사용자(또는 IAM 사용자)를 받아 1번부터 진행한다.

## 4. 설정 파일 만들기 (서버에서)

```bash
cd ~/Backend
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
openssl rand -hex 32      # TURN_SECRET에 넣을 값
openssl rand -base64 48   # JWT_SECRET에 넣을 값
nano deploy/.env          # 값 채우기 (저장: Ctrl+O, Enter / 나가기: Ctrl+X)
```

## 5. 백엔드 배포 (자동 배포)

처음 한 번 (서버에서):

```bash
cd ~/Backend
bash deploy/deploy.sh develop          # develop 최신 이미지를 받아 바로 띄운다
bash deploy/install-backend-cd.sh      # 2분마다 새 이미지를 확인하는 타이머 설치
```

끝나면 확인한다.

```bash
curl "https://zipfit.duckdns.org/api/auth/email-availability?email=test@test.com"
```

JSON이 나오면 성공이다.

## 6. 프론트 (같은 주소, 자동 배포)

처음 한 번 (서버에서):

```bash
cd ~/Backend
bash deploy/deploy-frontend.sh        # Frontend develop을 빌드해 바로 올린다 (~/frontend 생성)
bash deploy/install-frontend-cd.sh    # 2분마다 자동 배포하는 타이머 설치
```

`deploy-frontend.sh`를 docker compose보다 먼저 한 번 돌려야 `~/frontend`가 ubuntu 소유로 만들어진다.

**자동 배포 흐름**: Frontend `develop`에 머지 → GitHub Actions CI(lint·테스트·빌드) → 서버 타이머가 2분마다 새 커밋을 보고 **CI가 통과했을 때만** Node 컨테이너로 빌드해 `~/frontend/releases/<커밋>`에 두고 `current`를 한 번에 바꾼다.
빌드가 실패하거나 CI가 실패하면 기존 화면이 그대로 남는다. (서버가 가져가는 방식이라 GitHub에 AWS 자격 증명을 둘 필요가 없다.)

```bash
journalctl -u zipfit-frontend-deploy -n 30     # 자동 배포 기록
bash deploy/deploy-frontend.sh                  # 기다리지 않고 지금 배포
sudo systemctl disable --now zipfit-frontend-deploy.timer   # 자동 배포 끄기
```

- 프론트는 `VITE_API_BASE_URL`을 비워 빌드해서 API·WebSocket을 같은 주소로 보낸다. 같은 주소라 iOS Safari에서도 재발급 토큰 쿠키가 막히지 않는다.
- 카카오 지도 JavaScript 키는 `deploy/.env`의 `VITE_KAKAO_JS_KEY`. 바꾼 뒤 `bash deploy/deploy-frontend.sh`로 다시 빌드한다.
- **카카오 디벨로퍼스 등록**: 카카오 로그인 Redirect URI `https://zipfit.duckdns.org/oauth/kakao/callback`, 플랫폼 Web 사이트 도메인 `https://zipfit.duckdns.org`.

## 백엔드 자동 배포

**흐름**: Backend `develop`에 머지 → GitHub Actions가 테스트(`Build & Test`) → 통과하면 서버용(ARM) 이미지를 만들어 GHCR(`ghcr.io/mockdata-capstone/zipfit-backend:develop`, 커밋 SHA 태그도 함께)에 올림 → 서버 타이머가 2분마다 새 이미지를 보고 받아서 교체.
서버는 빌드하지 않고 이미지를 받기만 해서 부하가 거의 없다. 테스트가 실패하면 이미지가 올라가지 않아 기존 서버가 그대로 돈다.
교체할 때 15~20초 동안 API가 멈춘다. coturn은 설정이 바뀔 때만, caddy는 `Caddyfile`이 바뀔 때만 다시 띄운다. 업로드한 파일(프로필 이미지)과 HTTPS 인증서는 Docker 볼륨에 있어 남는다.

```bash
journalctl -u zipfit-backend-deploy -n 30                 # 자동 배포 기록
bash deploy/deploy.sh develop                             # 기다리지 않고 지금 배포
sudo systemctl stop zipfit-backend-deploy.timer           # 시연 중 잠깐 끄기 (다시 켜기: start)
sudo systemctl disable --now zipfit-backend-deploy.timer  # 완전히 끄기
```

- **되돌리기**: 타이머를 끄고, 되돌릴 커밋의 이미지로 띄운다.
  ```bash
  sudo systemctl stop zipfit-backend-deploy.timer
  cd ~/Backend/deploy && APP_TAG=<커밋 SHA 전체> docker compose up -d --force-recreate --no-deps app
  ```
  고친 커밋이 `develop`에 올라오면 `sudo systemctl start zipfit-backend-deploy.timer`로 다시 켠다.
- **GHCR을 쓸 수 없을 때**: `bash deploy/deploy.sh develop --build` 로 서버에서 직접 빌드한다 (5~10분, 메모리를 많이 써서 그동안 느려진다).
- **이미지 공개 설정 (처음 한 번)**: 서버가 로그인 없이 받도록 GitHub Organization → Packages → `zipfit-backend` → Package settings → Change visibility → **Public**.

## 자주 쓰는 명령

```bash
cd ~/Backend/deploy
docker compose ps                 # 상태
docker compose logs -f app        # 백엔드 로그 (나가기: Ctrl+C)
docker compose logs -f caddy      # HTTPS·프록시 로그
docker compose restart app        # 백엔드만 다시 시작
```

## 참고

- **비용**: AWS 무료 플랜 크레딧으로 쓴다. 남은 크레딧과 만료일은 콘솔 **Billing and Cost Management** 에서 보거나 `aws freetier get-account-plan-state --profile zipfit` 로 확인한다.
- **`ssh zipfit`이 안 될 때**: ① `aws sts get-caller-identity --profile zipfit` 이 실패하면 `aws login --profile zipfit` 으로 다시 로그인한다. ② `SessionManagerPlugin is not found` 가 나오면 플러그인을 설치했는지 확인하고 VS Code·터미널을 다시 연다. ③ `Permission denied (publickey)` 면 내 공개키가 서버 `~/.ssh/authorized_keys` 에 있는지 확인한다. 접속 장소(IP)가 바뀌는 것은 상관없다.
- **서버 비용 아끼기**: 쓰지 않을 때 `aws ec2 stop-instances --instance-ids i-07050950f9e4c87a2 --region ap-northeast-2 --profile zipfit` 로 끌 수 있다 (켜기: `start-instances`). 꺼 둬도 탄력적 IP와 디스크 비용은 나온다.
- **프론트 주소 설정**: `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `KAKAO_REDIRECT_URI`, `GOOGLE_REDIRECT_URI`는 `docker-compose.yml`이 `DOMAIN`으로 채운다 (`deploy/.env`에서 고치지 않는다). 로컬 프론트(`localhost:5173`)에서 이 서버를 불러도 되게 CORS에 함께 허용한다.
- **`TURN_PUBLIC_IP`·`TURN_PRIVATE_IP`**: `deploy.sh`가 배포할 때마다 `deploy/.env`에 적는다. 서버에서 `docker compose`를 직접 실행해도 coturn이 같은 값으로 뜬다.
