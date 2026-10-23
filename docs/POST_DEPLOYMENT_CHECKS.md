# 공개 배포 후 6개 항목 점검 보고서

작성일: 2026-10-23 한국 시간 · PR #6

**핵심 한 줄:** 실제 Vercel 화면부터 Hostinger API까지 검증하고, 실제 배포 구성과 제출 문서를 같은 기준으로 맞춘다.

사용자 요청에 따라 **컨테이너 재생성 영속성 시험·DB/파일 백업·복원 시험은 이번 범위에서 제외**한다. 기존 DB-O03·DP-O05·T25를 완료로 바꾸지 않는다.

| 순서 | 작업 | 이번 변경 | 실행 상태 |
|---|---|---|---|
| 1 | Vercel→서버 연결 | 실제 공개 주소의 HTML·정책 JSON·Origin 포함 세션 응답 검사, 출처 설정 도구 추가 | 공개 시험 통과 |
| 2 | 세션·CSRF | 쿠키의 도메인 범위·HttpOnly·Secure, CSRF 없는 변경 거부·정상 토큰 변경 검증 | 공개 시험 통과 |
| 3 | 공개 화면 정책·업로드 | 고정 선택 저장·새로고침·원복, 고유 커스텀 추가·차단 시험·삭제, 허용 파일 GUI 업로드·상태 조회 | 공개 시험 통과 |
| 4 | 크기 경계 | Vercel rewrite를 경유해 10MiB−1·10MiB·10MiB+1 전송, 화면의 초과 안내 검증 | 공개 시험 통과 |
| 5 | 배포 재현·복귀 | 실제 Traefik override 버전 관리, 보안 쿠키 설정 추가, 아래 배포·버전 기록·복귀 절차 작성 | 구성·절차 작성 완료; 실제 서버 이미지 ID/복귀는 미확인 |
| 6 | 문서·제출 | README·주석 가이드·제출 점검표를 실제 배포 기록과 연결 | 문서 수정 완료; 지원자 본인 최종 회고는 미작성 |

## 실제 실행 결과

2026-10-23 17:47~17:48 한국 시간, 실제 Vercel 프로덕션 주소를 대상으로 **11개 검사 모두 통과**했다. 소스 커밋은 1f5d82fffef910f8e84a3838f8e01ecab9881f3d다. 서버의 설정을 직접 수정한 것은 아니며, 앞선 기록에서 미확인이던 기능을 이번에 실제 확인했다.

| 검사 | 실제 결과 |
|---|---|
| HTML·React·고정 7개 | 200 및 화면 표시 |
| Vercel rewrite 정책 조회 | 200 JSON |
| Origin 포함 세션·쿠키 | 200, HttpOnly/Secure=true, SameSite=Lax |
| CSRF 없는 변경 | 403 CSRF_INVALID |
| 정상 토큰 고정 변경·새로고침·원복 | 통과 |
| 커스텀 추가·차단·삭제 | 통과, 차단422 EXTENSION_BLOCKED |
| 허용 파일 GUI 업로드·상태 조회 | 201 READY·조회200 |
| 10MiB−1 = 10,485,759바이트 | 201 READY·조회200 |
| 정확히10MiB = 10,485,760바이트 | 201 READY·조회200 |
| 10MiB+1 = 10,485,761바이트 | 413 UPLOAD_SIZE_LIMIT |
| GUI 크기 초과 안내 | 10MiB 안내 표시 |

[영구 증거 JSON](evidence/public-deployment-2026-10-23.json)에 전체 결과와 정리 여부를 보관했다. 정책 값 원복·시험 커스텀 삭제를 확인했다. 성공 업로드3개, 총20,971,548바이트는 서버에 남아 있다.

**남은 경계:** 서버의 현재 이미지 ID·Vercel 현재 Source·실제 버전 복귀는 관리 콘솔/서버 접근으로 확인해야 한다. 이번 PR은 그 절차와 실제 배포 override를 제공한다. 데모 로그인이 설정되지 않아 로그인 성공/로그아웃 시험은 수행하지 않았으며, 세션·CSRF와 인가를 구분한다. 지원자 최종 회고는 제출 점검표에 직접 작성한다.

## 실행 방법과 증거

- 시험 코드: [public-smoke.mjs](../frontend/e2e/public-smoke.mjs)
- 공개 시험 workflow: [public-deployment.yml](../.github/workflows/public-deployment.yml)
- 기존 격리 CI: [ci.yml](../.github/workflows/ci.yml)
- 최초 공개 실행: [39927104812](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39927104812)
- 회귀 시험: [39927108028](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39927108028)

최초 공개 쓰기 시험은 이 작업의 지정 브랜치에서 사용자 요청에 따라 실행했다. 시험 후 재사용 workflow는 **일반 PR에서 읽기 전용**으로 실행하도록 바꿨다. 이후 쓰기 시험은 main에 병합된 뒤 Actions의 **Public deployment verification → Run workflow**에서 `write_tests=true`를 명시한다. 입력 기본값은 false다. 읽기 전용 결과는 쓰기 시험을 NOT_REQUESTED로 구분하며, 이를 쓰기 성공으로 계산하지 않는다.

로컬에서 실행하려면 저장소 frontend에서:

```sh
npm ci
npx playwright install chromium
node e2e/public-smoke.mjs
# 사용자가 승인한 공개 쓰기 시험만:
PUBLIC_WRITE_TEST=true node e2e/public-smoke.mjs
```

Windows PowerShell에서는 `$env:PUBLIC_WRITE_TEST='true'` 설정 후 Node 명령을 실행하고, 끝나면 `Remove-Item Env:PUBLIC_WRITE_TEST`로 해제한다.

실행 결과는 Actions의 `public-deployment-evidence`에 JSON·화면 캡처로 남긴다. 쿠키 값·CSRF 토큰·비밀번호는 기록하지 않고 쿠키 속성만 검사한다. 실행 시점의 HTTP 결과와 원인을 구분한다. DNS 실패·403·502 등을 기능 코드의 시험 성공으로 대체하지 않는다.

### 정책 복원과 시험 파일

- 변경 전 exe 값을 기록하고 If-Match 버전으로 원복한다.
- 다른 방문자의 변경을 발견하면 덮어쓰지 않고 `MANUAL_REVIEW`를 남긴다. 정책 버전·감사 이력까지 되돌리지는 않는다.
- 커스텀은 실행마다 고유한 이름을 만들고 그 항목만 삭제한다. 기존 항목을 지워서 200개 제한의 여유를 만들지 않는다.
- 허용 파일과 크기 경계의 성공 업로드는 서버에 남는다. 삭제 API가 없으므로 자동 삭제하지 않는다. 예상 정상 저장량은 작은 파일 1개 + 10MiB−1 + 10MiB, 약 20MiB다. 실제 결과 JSON의 uploads에 요청 번호·파일 ID·크기·응답을 남긴다.
- 재전송은 자동으로 하지 않는다. 결과가 불확실하면 기록한 requestId로 상태를 확인한다.
- Origin/세션 전제 조건이 실패하면 후속 쓰기 시험은 BLOCKED로 표시한다. 이를 통과로 계산하지 않는다.
- 데모 로그인 사용 가능 여부와 정책 편집 인가는 별개다. BE-S11의 정책 인가·사용자 격리는 이번에도 추가하지 않는다.

## 격리 환경 회귀 검증

[Actions39927108028](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39927108028)에서 다음 항목이 모두 통과했다.

- 서버40개, Node10개, 브라우저16개
- Origin 설정 도구4개, 공개 검증 스크립트 구문 검사
- 최소 권한 앱 계정으로 T01~T04 실제 Java 프로세스 재시작
- 기본 Compose·실제 Traefik override·계획 Caddy 구성 유효성, 기존 Nginx 413 계약

이 실행의 PR merge tree를 검증했으며 공개 시험11개와는 별도 환경이다. Java 프로세스 재시작은 기존 회귀 항목이고, 이번에 제외한 운영 컨테이너 재생성·백업·복원 시험을 수행한 것은 아니다.

## 실제 서버 설정 적용

이 절차는 **Hostinger VPS 콘솔**에서 실행한다. 이 PR의 코드가 main에 병합된 후 저장소를 갱신한다. 기존 환경 파일의 암호를 출력하거나 Git에 올리지 않는다.

```bash
cd /docker/flow-fileupload-ai
git checkout main
git pull --ff-only

python3 scripts/configure-live-origin.py
python3 scripts/configure-live-origin.py --apply

export APP_VERSION="$(git rev-parse HEAD)"
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml config --quiet
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml build backend frontend
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml up -d --no-deps backend frontend
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml restart frontend
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml ps -a
```

서버의 기존 `compose.vps-live.yml`은 처음 배포 때 생성한 비추적 파일일 수 있다. `git pull`이 해당 파일을 덮어쓸 수 없다고 거절하면 **그 파일 하나만 서버의 별도 경로로 보관**하고 다시 pull한다. 환경 파일이나 볼륨은 이동·삭제하지 않는다. 예: `cp -p compose.vps-live.yml /root/flow-compose-before-pr6.yml` 후 비교하여, 기존 파일을 Git이 추적하는 파일과 충돌하지 않는 경로로 옮긴다.

`configure-live-origin.py`는 기존 Origin을 보존하고 Vercel 주소를 추가한다. 기본 실행은 검증만 하고 `--apply`일 때 원자적으로 교체하며 권한을 600으로 설정한다. 중복 키·지원하지 않는 표현은 자동 추정하지 않고 거절한다. 실제 비밀값은 출력하지 않는다.

이번 override는 기존 Traefik 라우팅을 옮기고 운영 세션에 `SESSION_COOKIE_SECURE=true`를 추가했다. 서버에 적용됐다는 뜻은 아니다. `APP_PORT=18081`과 기존 네트워크 `flow-fileupload_default`를 유지한다. Caddy 구성을 함께 실행하지 않는다.

코드만 반영한 PR이므로 DB 스키마 변경은 없다. 위 명령은 DB·볼륨·migrate 컨테이너를 재생성하지 않는다. **백업·복원·영속성 시험 명령도 실행하지 않는다.**

## 배포 버전과 복귀 절차

프론트와 서버가 서로 다른 커밋으로 실행될 수 있으므로 두 버전과 변경 범위를 각각 기록한다. GitHub main 변경만으로 Hostinger가 자동 갱신되지는 않는다.

```bash
# 같은 터미널에서 APP_VERSION을 설정한 뒤 실행
printf 'SOURCE_COMMIT=%s\n' "$APP_VERSION"
docker image inspect "flow-policy-backend:$APP_VERSION" --format '{{.Id}}'
docker image inspect "flow-policy-frontend:$APP_VERSION" --format '{{.Id}}'
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml ps -a
```

| 기록 | 현재 확인된 값 또는 기입 위치 |
|---|---|
| 최초 Hostinger 소스 | bbb930ef05e4fd556f75709ee6f0f23ca7cb6531 |
| Vercel rewrite 변경 | c888541b67008da275b28dd974ecc144c18b8e08 |
| 현재 서버 실행 이미지 ID | 위 명령으로 확인 후 배포 기록에 추가; 아직 확보하지 않음 |
| 현재 Vercel 배포 커밋 | Vercel Deployment의 Source에서 확인; GitHub 최신 main으로 추정하지 않음 |
| 운영 빌드 로그 | 기존 /tmp 로그는 임시 파일. 이후 비밀값 없는 로그를 별도 운영 경로에 보관 |
| 자동 배포 검증 | Vercel은 저장소 연결 확인; 후속 push 재배포·Hostinger 갱신은 별도 기록 |

복귀 전에는 이전 이미지의 존재와 변경된 migration 파일 유무를 확인한다. 이번 PR에는 migration 변경이 없지만, 다른 배포에서도 항상 호환성을 다시 판단한다.

```bash
# 기록해 둔 이전 전체 커밋을 넣는다. 임의의 태그/latest로 바꾸지 않는다.
export APP_VERSION=bbb930ef05e4fd556f75709ee6f0f23ca7cb6531
docker image inspect "flow-policy-backend:$APP_VERSION" --format '{{.Id}}'
docker image inspect "flow-policy-frontend:$APP_VERSION" --format '{{.Id}}'
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml up -d --no-deps backend frontend
docker compose -p flow-fileupload -f compose.yml -f compose.vps-live.yml restart frontend
```

이전 이미지가 없으면 즉석에서 구버전을 빌드했다고 가정하지 않는다. 해당 소스를 별도 작업 디렉터리에서 빌드하거나 복귀 계획을 다시 세운다. DB 마이그레이션 역실행·볼륨 초기화는 하지 않는다. Vercel 복귀도 Deployment의 이전 성공 배포를 선택하고 공개 URL의 조회·세션을 다시 확인한다. **이번 작업에서 실제 서버 복귀를 실행하지 않았으므로 복귀 시험 통과로 표기하지 않는다.**

## 문서와 보류 결정

[제출 점검표](SUBMISSION_CHECKLIST.md), [주석 가이드](IMPLEMENTATION_GUIDE_ANNOTATED.md), [배포 진행 기록](DEPLOYMENT_PROGRESS_2026-10-23.md)을 함께 읽는다.

정상 파일 보관/총량(DEC-13), 정책 인가(BE-S11), cron 설치(DB-O05), 신규 readiness/자동복구(BE-O07), FE-U07 확장은 기존 보류 결정을 유지한다. 내용 진단(BE-S06) 결과를 운영 내용 차단이나 악성코드 검사 완료로 확대하지 않는다.
