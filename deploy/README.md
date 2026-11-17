# AWS EC2 배포

EC2 한 대에서 Docker로 세 가지를 함께 띄운다.

| 컨테이너 | 역할 |
|---|---|
| `app` | 백엔드 (Spring Boot) |
| `caddy` | HTTPS 인증서 자동 발급·갱신, 요청·WebSocket을 백엔드로 전달 |
| `coturn` | TURN 서버. 이동통신망처럼 P2P가 막힌 환경에서 WebRTC 영상을 중계한다 |

DB는 Neon을 그대로 쓴다. EC2를 Neon과 같은 **싱가포르(ap-southeast-1)** 에 두어 쿼리 지연을 줄인다.

---

## 1. EC2 만들기 (AWS 콘솔)

1. 콘솔 오른쪽 위에서 리전을 **아시아 태평양(싱가포르) ap-southeast-1** 로 바꾼다.
2. **EC2 → 인스턴스 시작**
   - 이름: `zipfit`
   - AMI: **Ubuntu Server 24.04 LTS**
   - 인스턴스 유형: **t3.small** (메모리 2GB). 목록에 "프리 티어 사용 가능"이 붙은 유형만 쓸 수 있다.
   - 키 페어: **새 키 페어 생성** → 이름 `zipfit`, RSA, `.pem` → 내려받은 파일을 잘 보관한다 (다시 받을 수 없다)
   - 스토리지: **20 GiB, gp3**
   - 네트워크 설정 → **편집** → 보안 그룹 규칙을 아래처럼 만든다

     | 유형 | 프로토콜 | 포트 | 소스 | 용도 |
     |---|---|---|---|---|
     | SSH | TCP | 22 | **내 IP** | 서버 접속 |
     | HTTP | TCP | 80 | 0.0.0.0/0 | 인증서 발급, https로 넘기기 |
     | HTTPS | TCP | 443 | 0.0.0.0/0 | API·WebSocket |
     | 사용자 지정 UDP | UDP | 443 | 0.0.0.0/0 | HTTP/3 (선택) |
     | 사용자 지정 TCP | TCP | 3478 | 0.0.0.0/0 | TURN |
     | 사용자 지정 UDP | UDP | 3478 | 0.0.0.0/0 | TURN |
     | 사용자 지정 UDP | UDP | 49160-49200 | 0.0.0.0/0 | TURN 중계 |
3. **탄력적 IP** (서버를 껐다 켜도 IP가 바뀌지 않게)
   - EC2 → 네트워크 및 보안 → **탄력적 IP → 탄력적 IP 주소 할당 → 할당**
   - 할당된 IP 선택 → **작업 → 탄력적 IP 주소 연결** → 인스턴스 `zipfit` 선택

## 2. 도메인 연결 (DuckDNS, 무료)

HTTPS 인증서를 받으려면 도메인이 필요하다.

1. https://www.duckdns.org 에 GitHub 계정으로 로그인
2. sub domain에 이름(예: `zipfit`)을 넣고 **add domain** → `zipfit.duckdns.org`
3. 그 줄의 **current ip**에 탄력적 IP를 넣고 **update ip**

## 3. 서버 접속 (Windows PowerShell)

```powershell
# 처음 한 번: 키 파일을 나만 읽을 수 있게 한다 (안 하면 ssh가 키를 거부한다)
icacls "$HOME\Downloads\zipfit.pem" /inheritance:r /grant:r "$($env:USERNAME):(R)"

ssh -i "$HOME\Downloads\zipfit.pem" ubuntu@<탄력적 IP>
```

## 4. 처음 설치 (서버에서)

```bash
git clone https://github.com/MockData-Capstone/Backend.git
cd Backend
bash deploy/setup.sh      # Docker 설치, 스왑 2GB 추가
exit                      # 로그아웃 후 다시 ssh로 접속한다 (docker 그룹 적용)
```

다시 접속한 뒤 설정 파일을 만든다.

```bash
cd Backend
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

- **비용**: AWS 무료 플랜 크레딧으로 쓴다. 무료 플랜에서는 요금이 청구되지 않고, 6개월이 지나거나 크레딧을 다 쓰면 끝난다. 남은 크레딧은 콘솔 **Billing → Credits** 에서 본다.
- **SSH가 안 될 때**: 보안 그룹의 SSH는 "내 IP"만 허용한다. 학교·집처럼 접속 장소가 바뀌면 보안 그룹에서 SSH 규칙의 소스를 다시 **내 IP**로 고친다.
- **프론트를 배포하면**: `deploy/.env`의 `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `KAKAO_REDIRECT_URI`, `GOOGLE_REDIRECT_URI`를 프론트 주소로 바꾸고, 카카오 디벨로퍼스(Redirect URI, 플랫폼 Web 도메인)에도 같은 주소를 등록한다.
