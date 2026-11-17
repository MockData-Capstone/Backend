# AWS EC2 배포

EC2 한 대에서 Docker로 세 가지를 함께 띄운다.

| 컨테이너 | 역할 |
|---|---|
| `app` | 백엔드 (Spring Boot) |
| `caddy` | HTTPS 인증서 자동 발급·갱신, 요청·WebSocket을 백엔드로 전달 |
| `coturn` | TURN 서버. 이동통신망처럼 P2P가 막힌 환경에서 WebRTC 영상을 중계한다 |

AWS 프로젝트(새로운 AWS 경험)는 리소스를 **아시아 태평양(시드니) ap-southeast-2** 한 곳에만 만들 수 있다.
그래서 배포 서버용 DB도 Neon **시드니**에 따로 만들어 서버 바로 옆에 둔다. 팀 개발용 Neon(싱가포르)은 그대로 쓴다.

---

## 0. 배포용 DB 만들기 (Neon)

1. Neon 콘솔 → **New Project** → Region: **AWS Asia Pacific 2 (Sydney)** → 이름 예: `zipfit-prod`
2. 만들어진 프로젝트의 **Connect**에서 연결 문자열 `postgresql://<user>:<password>@<host>/<db>?sslmode=require...` 를 확인한다.
3. 아래 4단계에서 `deploy/.env`에 이렇게 옮긴다 (풀러 주소 `-pooler`가 붙은 host를 쓴다).
   - `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require&channelBinding=require`
   - `DB_USERNAME=<user>`, `DB_PASSWORD=<password>`

테이블은 서버가 처음 뜰 때 만들어진다.

## 1. EC2 (만들어 둠, 2026-11-17)

| 리소스 | 값 |
|---|---|
| 인스턴스 | `zipfit-backend` (`i-01e92d28d3eb4c3d9`), t4g.small (ARM, 2GB), Ubuntu 24.04, gp3 20GB 암호화 |
| 탄력적 IP | **`16.178.202.175`** (서버를 껐다 켜도 바뀌지 않는다) |
| 보안 그룹 | `zipfit-backend-sg` — TCP 80·443, TCP·UDP 3478, UDP 49160-49200. **SSH(22)는 열지 않는다** |
| IAM 역할 | `zipfit-backend-role` — `AmazonSSMManagedInstanceCore`만 (Session Manager 접속용) |
| 기타 | IMDSv2만 허용, 종료 보호 켬, CPU 크레딧 standard (추가 요금 없음) |

첫 부팅 때 Docker 설치, 스왑 2GB, 저장소(`~/Backend`) 받기가 자동으로 끝났다 (`deploy/setup.sh`와 같은 일).
서버를 새로 만들 때만 `deploy/setup.sh`를 직접 실행한다.

## 2. 도메인 연결 (DuckDNS, 무료)

HTTPS 인증서를 받으려면 도메인이 필요하다.

1. https://www.duckdns.org 에 GitHub 계정으로 로그인
2. sub domain에 이름(예: `zipfit`)을 넣고 **add domain** → `zipfit.duckdns.org`
3. 그 줄의 **current ip**에 탄력적 IP `16.178.202.175`를 넣고 **update ip**

## 3. 서버 접속 (SSH over Session Manager)

22번 포트를 열지 않고, SSH를 AWS Session Manager 통로로 보낸다. `ssh`·`scp`·VS Code Remote-SSH가 그대로 된다.

**브라우저에서 바로 (설치 없음)**: AWS 콘솔 → EC2 → `zipfit-backend` → **연결 → Session Manager → 연결**.
`ssm-user`로 들어가므로 먼저 `sudo -iu ubuntu` 를 입력한다.

**내 PC에서 `ssh zipfit` (처음 한 번 설정)**

1. AWS CLI 로그인: `aws login --profile zipfit` (12시간마다 자동 갱신, 90일마다 다시 로그인)
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
       HostName i-01e92d28d3eb4c3d9
       User ubuntu
       IdentityFile ~/.ssh/zipfit_ed25519
       IdentitiesOnly yes
       ServerAliveInterval 30
       ProxyCommand C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "$env:Path = 'C:\Program Files\Amazon\SessionManagerPlugin\bin;' + $env:Path; & 'C:\Users\<내 계정>\AppData\Local\Programs\Amazon\AWSCLIV2\aws.exe' ssm start-session --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p --region ap-southeast-2 --profile zipfit"
   ```
5. 접속: `ssh zipfit` (VS Code: Remote-SSH → `zipfit`)

팀원이 접속하려면 AWS Settings에서 프로젝트 **팀 멤버**로 초대받아 1번부터 진행한다.

## 4. 설정 파일 만들기 (서버에서)

```bash
cd ~/Backend
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
openssl rand -hex 32      # TURN_SECRET에 넣을 값
openssl rand -base64 48   # JWT_SECRET에 넣을 값
nano deploy/.env          # 값 채우기 (저장: Ctrl+O, Enter / 나가기: Ctrl+X)
```

## 5. 배포

```bash
bash deploy/deploy.sh develop
```

처음에는 이미지를 빌드하느라 5~10분 걸린다. 끝나면 확인한다.

```bash
curl "https://zipfit.duckdns.org/api/auth/email-availability?email=test@test.com"
```

JSON이 나오면 성공이다. 프론트에서는 `VITE_API_BASE_URL=https://zipfit.duckdns.org` 로 이 서버를 쓴다.

## 다시 배포할 때

```bash
cd ~/Backend && bash deploy/deploy.sh develop
```

`develop`의 최신 코드를 받아 다시 빌드하고, 바뀐 컨테이너만 다시 띄운다. 업로드한 파일(프로필 이미지)과 HTTPS 인증서는 Docker 볼륨에 있어 남는다.

## 자주 쓰는 명령

```bash
cd ~/Backend/deploy
docker compose ps                 # 상태
docker compose logs -f app        # 백엔드 로그 (나가기: Ctrl+C)
docker compose logs -f caddy      # HTTPS·프록시 로그
docker compose restart app        # 백엔드만 다시 시작
```

## 참고

- **비용**: AWS 무료 플랜 크레딧으로 쓴다. 무료 플랜에서는 요금이 청구되지 않고, 6개월이 지나거나 크레딧을 다 쓰면 끝난다. 남은 크레딧과 만료일은 **AWS Settings → Billing** 에서 보거나 `aws freetier get-account-plan-state` 로 확인한다.
- **`ssh zipfit`이 안 될 때**: ① `aws sts get-caller-identity --profile zipfit` 이 실패하면 `aws login --profile zipfit` 으로 다시 로그인한다. ② `SessionManagerPlugin is not found` 가 나오면 플러그인을 설치했는지 확인하고 VS Code·터미널을 다시 연다. ③ `Permission denied (publickey)` 면 내 공개키가 서버 `~/.ssh/authorized_keys` 에 있는지 확인한다. 접속 장소(IP)가 바뀌는 것은 상관없다.
- **서버 비용 아끼기**: 쓰지 않을 때 `aws ec2 stop-instances --instance-ids i-01e92d28d3eb4c3d9 --region ap-southeast-2 --profile zipfit` 로 끌 수 있다 (켜기: `start-instances`). 꺼 둬도 탄력적 IP와 디스크 비용은 나온다.
- **프론트를 배포하면**: `deploy/.env`의 `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `KAKAO_REDIRECT_URI`, `GOOGLE_REDIRECT_URI`를 프론트 주소로 바꾸고, 카카오 디벨로퍼스(Redirect URI, 플랫폼 Web 도메인)에도 같은 주소를 등록한다.
