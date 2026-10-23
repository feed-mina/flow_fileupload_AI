# 파일 업로드 정책 — DEC 검증·저장

**확장자 정책을 PostgreSQL에 저장하고, 서버에서 검증한 업로드만 UUID 파일과 적용 정책 버전을 남긴다.**

React 19 + Ant Design 6 화면이 Spring Boot 3.5 / Java 17의 JSON API를 호출합니다. PostgreSQL 17에 정책을 보관하고 Flyway로 최초 데이터를 한 번 생성합니다. Docker Compose로 화면·서버·DB를 실행하며 GitHub PR과 Actions에 변경 및 시험 근거를 남깁니다.

PR #1의 T01~T04에 이어 실제 업로드 차단·저장과 커스텀 삭제를 구현했습니다. [채택한 DEC와 구현 계약](docs/DEC_IMPLEMENTATION.md)을 기준으로 합니다. 인증·사용자별 격리, 보관 기간·전체 저장 한도가 아직 결정되지 않아 로컬 구성만 제공하며 공개 배포는 이번 범위에 포함하지 않습니다.

## 실행

Docker 엔진이 실행 중인 환경에서 저장소 루트에서 진행합니다.

```sh
cp .env.example .env
# .env의 POSTGRES_PASSWORD를 로컬용 값으로 변경
docker compose up --build -d
```

브라우저에서 http://localhost:8080 을 엽니다. 포트는 `.env`의 `APP_PORT`로 변경합니다. DB와 서버 포트는 외부에 공개하지 않으며 화면 포트도 127.0.0.1에만 연결합니다. 인증이 없는 로컬 과제용 구성입니다.

```sh
docker compose logs backend
docker compose restart backend
docker compose down
docker compose up -d
```

이 명령은 `policy_data` DB 볼륨과 `upload_data` 파일 볼륨을 유지합니다. `down -v`는 두 데이터를 모두 지우므로 재시작 시험에 사용하지 마세요. DB 암호를 최초 생성 이후 바꾸려면 기존 PostgreSQL 사용자 암호도 함께 관리해야 합니다. 실제 Docker 빌드·기동·컨테이너 재생성 검증은 TEST_RESULTS의 실행 상태를 확인하세요.

## 최소 시험

| 시나리오 | 동작 | 통과 조건 |
|---|---|---|
| T01 | 새 DB에서 첫 실행 | bat/cmd/com/cpl/exe/scr/js 7개 미선택, 커스텀 0개 |
| T02 | exe 선택 → 새로고침 | 선택 유지, DB enabled=true |
| T03 | exe 해제, scr 선택 → Java 프로세스 종료·재실행 | exe=false, scr=true, 버전·행 ID 유지 |
| T04 | sh 추가 → 재조회 | 목록 sh, 카운트 1/200, DB 1행 |

재시작 시험은 `scr=true`도 남겨서, 초기값으로 전부 리셋된 오류를 `exe=false`만 보고 통과시키지 않습니다. `scripts/verify-restart.mjs`는 두 개의 실제 Java 프로세스를 순서대로 실행하고 같은 PostgreSQL을 사용합니다.

```sh
mvn -B -ntp -f backend/pom.xml verify
cd frontend
npm ci
npm test
npm run build
npx playwright install --with-deps chromium
```

서버 통합 시험은 Testcontainers 전용 PostgreSQL을 생성하므로 Docker가 필요합니다. 브라우저 시험은 새 `flow_browser` DB에 대한 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 설정한 뒤 `frontend`에서 `npm run test:e2e`로 실행합니다. 기존 앱 DB를 시험 대상으로 사용하지 마세요. 실제 재시작 시험은 루트에서 새 `flow_smoke` DB 환경변수를 설정하고 `node scripts/verify-restart.mjs`로 실행합니다. 이미 사용한 시험 DB는 T01의 초기 조건을 충족하지 않습니다.

- [시험 결과와 실행 제한](docs/TEST_RESULTS.md)
- [API 계약 및 임시 정책](docs/POLICY_SLICE.md)
- [DEC 채택·업로드 계약·장애 후 대조](docs/DEC_IMPLEMENTATION.md)
- [기존 구현 가이드](docs/IMPLEMENTATION_GUIDE.md)
- [요청·진행 기록](docs/PROMPT_LOG.md)

## 구조와 리서치 적용

| 영역 | 구현 | 선택 이유 |
|---|---|---|
| 화면 | `frontend/src/App.jsx`, `usePolicy.js` | Ant Design의 Checkbox/Input/Alert로 상태·오류를 일관되게 표시 |
| API | `backend/src/main/java/dev/mina/flow/policy` | Controller → Service 트랜잭션 → JDBC 파라미터 바인딩 |
| 데이터 | `backend/src/main/resources/db/migration` | Flyway 최초 삽입, UNIQUE/CHECK 제약, 원자적 버전 갱신 |
| 운영 | `compose.yml`, 각 Dockerfile | DB 볼륨 유지, 백엔드 비루트 사용자, 동일 출처 API 프록시 |
| 검증 | `backend/src/test`, `frontend/e2e`, `scripts` | 실제 PostgreSQL·브라우저·프로세스 재시작 근거 분리 |

작은 단일 화면에는 별도 상태관리 라이브러리를 추가하지 않고 React 훅으로 조회 취소·중복 저장 방지를 구현했습니다. shadcn 및 dropzone은 이번 범위에 도입하지 않았습니다. Spring 업로드 예제의 시작 시 디렉터리 삭제 로직도 사용하지 않습니다. Ant Design은 npm 패키지로 사용하며 소스 복사·라이선스 헤더 제거는 하지 않습니다.

## 업로드·삭제 사용

커스텀 항목의 × 버튼으로 삭제합니다. 변경은 같은 버전 비교·정책 행 잠금 규칙을 따르며 성공 응답 이후 목록과 개수를 갱신합니다.

업로드 영역에서 파일 1개를 선택해 전송합니다. 서버는 마지막 점 뒤의 확장자를 비교하므로 `report.exe.txt`는 txt, `archive.tar.gz`는 gz입니다. `README`, `.env`, `file.`, 경로·제어문자가 있는 이름은 사유를 나눠 거부합니다. 파일당 10 MiB, 요청 전체 12 MiB, 이름은 UTF-8 255바이트 이내이며 0바이트는 거부합니다.

성공 응답에는 파일 ID·원본 표시 이름·실제 바이트 크기·적용 정책 버전·READY 상태가 들어갑니다. 본문은 `UPLOAD_ROOT`(Compose: `/app/uploads`)에 UUID `.bin`으로 저장하며 공개 URL은 제공하지 않습니다. MIME과 확장자는 파일 안전성 보증이 아닙니다. 내용·악성코드 검사는 도입하지 않았습니다.

응답을 받지 못한 업로드는 자동 재전송하지 않습니다. 강제 종료나 DB 커밋 결과 불명 시에는 DB·파일을 대조해야 하며, [장애 후 대조 절차](docs/DEC_IMPLEMENTATION.md)를 따릅니다.


## UX·예외 처리 후속 구현

[채택 항목·응답 유실 조회 계약·시험 범위](docs/UX_IMPLEMENTATION.md)를 확인하세요. 미확인 업로드는 화면의 저장 결과 확인 버튼 또는 GET /api/uploads/status/{requestId}로 조회합니다. 404는 아직 처리 중일 수 있으며 자동 재전송하지 않습니다. 정책 변경·버전 증가·감사 이력은 함께 커밋됩니다. FE-U07은 이번 추가 구현 범위에서 제외했습니다.


## 구현 결과 보고서

[PR1·PR2·PR3 구현 결과와 UI 리서치·가이드 전체 목록](docs/reports/README.md)을 확인하세요. HTML은 오른쪽 노드와 왼쪽 소스를 연결하며, Markdown은 GitHub 소스 줄 링크를 제공합니다.


## Hostinger·Vercel 배포 준비

현재 구현·보류 항목은 [주석판 구현 가이드](docs/IMPLEMENTATION_GUIDE_ANNOTATED.md) 23절을 따릅니다. [Hostinger VPS 서버·PostgreSQL / Vercel 프론트 배포 준비](docs/HOSTINGER_VERCEL_DEPLOYMENT.md), [감사·로그 매일 02시/일요일 03시 예약 안내](docs/OPERATIONS_SCHEDULE_GUIDE.md)를 함께 확인하세요.

로컬 새 DB의 관리자 계정은 flow_admin, 앱 계정은 flow_app입니다. `.env`에 서로 다른 `POSTGRES_PASSWORD`와 `APP_DB_PASSWORD`를 지정합니다. 관리자 마이그레이션은 일회성 migrate 서비스에서 실행하고, backend에는 앱 자격만 전달합니다. 기존 볼륨은 초기화 스크립트가 재실행되지 않으므로 배포 준비 문서의 기존 DB 절차를 먼저 따릅니다.

쿠키 로그인은 `DEMO_AUTH_USER`/12자 이상 `DEMO_AUTH_PASSWORD`를 직접 설정한 경우에만 사용합니다. 기본 로그인 계정은 없습니다. 로그인 여부와 별개로 현재 정책 API의 사용자별 격리/편집 권한은 적용하지 않았습니다. 변경 API는 먼저 GET /api/session으로 쿠키·CSRF 토큰을 받고 X-CSRF-TOKEN을 보내야 합니다. 직접 API 도구와 새로 시작한 세션도 같습니다.

기본 요청 제한은 연결 주소당 변경 요청 120회/분, 동시 업로드4개입니다. 정상 저장 파일의 전체 총량/보관 정책은 미구현입니다. 만료 임시 파일만 별도로 청소합니다. 실제 공개 배포·cron 설치·백업/복원 자동화는 이 변경에서 실행하지 않았습니다.
