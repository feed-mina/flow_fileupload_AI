# 파일 업로드 정책 — T01~T04

**고정 확장자 선택과 커스텀 확장자를 PostgreSQL에 저장하고, 새로고침·서버 재시작 뒤에도 동일한 정책을 조회한다.**

React 19 + Ant Design 6 화면이 Spring Boot 3.5 / Java 17의 JSON API를 호출합니다. PostgreSQL 17에 정책을 보관하고 Flyway로 최초 데이터를 한 번 생성합니다. Docker Compose로 화면·서버·DB를 실행하며 GitHub PR과 Actions에 변경 및 시험 근거를 남깁니다.

이번 범위는 T01~T04입니다. 파일 업로드 차단 실행, 커스텀 삭제, 인증·사용자별 정책은 구현하지 않았습니다. 전체 과제 완료를 의미하지 않습니다.

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

이 명령은 `policy_data` 볼륨을 유지합니다. `down -v`는 데이터를 지우므로 재시작 시험에 사용하지 마세요. DB 암호를 최초 생성 이후 바꾸려면 기존 PostgreSQL 사용자 암호도 함께 관리해야 합니다.

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
