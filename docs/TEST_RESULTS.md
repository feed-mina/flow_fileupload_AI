# Hostinger·Vercel 준비 검증 — PR #5

[실제 CI 실행 39914038649](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39914038649)에서 서버40개·Node10개·브라우저16개·별도 관리자 마이그레이션/최소 권한 재시작 T01~T04·두 Compose/Caddy 구성·Nginx 계약이 모두 통과했다. 실제 Hostinger/Vercel 공개 배포는 미실행이다.

| 검증 | 실제 결과 | 범위 |
|---|---|---|
| 서버 | 40개 통과, 실패/오류/skip 0 | 실제 PostgreSQL·최소 권한·CSRF·저장/정리·구조화 로그·내용 진단 |
| Node API | 10개 통과 | CSRF 핸드셰이크와 요청/오류/재전송 금지 회귀 |
| 브라우저 | 16개 통과, retry 0 | 정책/업로드 실서버 경로4개·모킹 경로12개; 두 탭·60초·사전 안내·초기화·429·모바일 |
| 재시작/마이그레이션 | T01~T04 통과 | 별도 관리자 migrator 뒤 flow_app으로 두 실제 Java 프로세스 실행 |
| 배포 구성 | 두 Compose 구성·Caddy validate·별도 실제 Nginx 413 통과 | 전체 Hostinger 앱 이미지 빌드/기동·공개 HTTPS 배포는 미실행 |
| 내용 진단 | PDF/PNG/GIF 일치/불일치·정책 이름207개·등록 이름1284개 | 등록 이름을 PNG 내용으로 변경: MATCH 1·MISMATCH 1206·UNVERIFIABLE 77. 모든 형식의 양성 fixture/내부 유효성 검증은 아님 |

**DB 실측:** 통계 갱신·예열20회 후 각 크기에서 읽기200회, 동시 쓰기4개/총40회. 읽기는 서비스 조회, 쓰기는 부모 잠금·변경·버전·감사 DB 트랜잭션이다. 격리 CI 환경이며 HTTP/네트워크/Hostinger 실서비스 지연은 포함하지 않는다. p95는 측정값95%가 그 시간 이내였다는 뜻이다.

| 커스텀 개수 | 읽기 p50 / p95 / p99(ms) | 쓰기 p50 / p95 / p99(ms) | 읽기 / 쓰기 초당 처리량 |
|---:|---:|---:|---:|
| 0 | 1.830 / 3.640 / 4.495 | 7.538 / 16.185 / 27.915 | 436.0 / 412.4 |
| 50 | 1.681 / 2.081 / 2.262 | 6.918 / 10.090 / 16.423 | 577.5 / 521.7 |
| 200 | 1.574 / 1.808 / 1.926 | 6.093 / 7.684 / 8.557 | 629.3 / 626.4 |

실제 계획은 세 크기 모두 Seq Scan과 정렬을 선택했다. 7~207행의 작은 데이터에서 이를 인덱스 장애로 해석하지 않는다. UNIQUE(policy_id, extension)는 중복 제약으로 유지하고 이번 측정만으로 추가 인덱스를 만들지 않았다.

**모바일 실측:** 390×844 화면·20자 이름200개, API 모킹. 3회 지연 1465 / 944 / 934ms, p95(3회 소표본에서는 최댓값) 1465ms. 가로폭 390/390px, 삭제 버튼44px 이상·삭제 후199개를 확인했다. 실기기/API 지연/프레임률 측정은 아니다.

[실행 로그](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39914038649)의 policy-test-evidence에 db-performance.json, content-extension-matrix.json, mobile-200-performance.json·모바일 캡처·JUnit/브라우저/재시작 증거를 보관한다. 재실행 시 수치는 달라질 수 있다.


이 시험은 공개 Hostinger/Vercel 서비스의 지연이나 실제 HTTPS/CORS/쿠키 전달을 검증한 것이 아닙니다. 권한 시험은 격리된 PostgreSQL과 별도 앱/관리 연결에서 수행합니다. 재시작 시험은 별도 migrator로 스키마를 만든 뒤 flow_app 계정과 Flyway 비활성으로 두 실제 Java 프로세스를 실행합니다.

내용 진단은 Apache Tika test 의존성으로 MIME/시그니처를 비교합니다. 유효한 파일 구조 전체·악성코드 부재를 보장하지 않고 운영 업로드 차단을 추가하지 않았습니다. 모바일 시험의 API 응답은 모킹합니다. 실제 두 탭 충돌·기존 정책/업로드 흐름은 실제 서버 경로로 시험합니다. 강제 종료 시험은 자식 Java 파일 쓰기 fixture를 실제 종료하고 새 UploadStorage에서 만료 잔여물 정리를 확인합니다.

초기 CI에서 multipart 요청 builder에 CSRF 처리기를 먼저 붙여 구체 타입이 사라지는 컴파일 문제와 tag@digest 문자열의 Testcontainers 호환 판정 문제를 발견했습니다. 요청 builder의 구체 타입을 유지하고 공식 PostgreSQL digest-only 참조로 고쳤습니다. 시험을 생략하거나 CSRF 검사를 끄지 않았습니다. 이후 정상 실행의 근거는 위 새 CI 링크를 따릅니다.

아래 기록은 PR1~3의 과거 실행입니다.

# UX·예외 후속 구현 검증

로컬 Node API 10개 PASS. 첫 UX 구현 커밋 `f605e640965336730ef6c744577a5610622f55c0`의 [실제 CI](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39902350802)는 서버 30개·Node 10개·브라우저 11개·실제 재시작 T01~T04·실제 Nginx 413·설치/빌드/Compose config 모두 PASS. 서버 skip 0, 브라우저 retry 0. 이후 정리한 최종 커밋의 판정과 실행 URL은 [PR #3 본문](https://github.com/feed-mina/flow_fileupload_AI/pull/3)·[Checks](https://github.com/feed-mina/flow_fileupload_AI/pull/3/checks)를 따른다. 상세 구성·실행 범위는 [UX_IMPLEMENTATION](UX_IMPLEMENTATION.md)을 참조한다. 아래 PR2/PR1 기록은 과거 실행이며 새 커밋 통과 판정이 아니다.

# T01~T04 시험 결과

## DEC 후속 검증 — PR #2

- 로컬: Node API 6개 PASS. 삭제 요청·multipart 헤더·READY 판정 포함.
- 서버 통합·파일 장애·경합·HTTP 크기 경계: 두 번째 CI에서 24개 PASS, skip 0. 기존 T01~T04 실제 재시작과 프론트 빌드도 PASS.
- 두 번째 CI 브라우저 기록: 3/5 PASS, 실제 서버 경로 2개 실패. Vite 문자열 프록시가 Host를 변경해 정상 Origin 요청이 거부되는 원인을 확인하고 객체 설정의 `changeOrigin: false`로 수정. 보안 검사를 비활성화하지 않음.
- 실제 Docker 이미지 빌드·Compose 기동·공개 배포·컨테이너 재생성: 미실행.
- 실행: https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39896452446

첫 실행의 실제 HTTP 크기 경계 시험은 초과 파일의 JSON 413 대신 연결 끊김으로 실패했다. Tomcat의 중단 업로드 소비 한도를 무제한으로 풀지 않고 12 MiB로 조정한 뒤, 두 번째 실행에서 10 MiB-1·정확히·+1바이트의 201·201·413을 확인했다.

두 번째 실행: https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39896632010 . 기대한 장애 주입의 ERROR 로그는 503·롤백·파일 정리가 통과했는지와 함께 해석한다. 모든 ERROR 로그를 새로운 장애로 세지 않는다.

위 두 실행은 수정 과정의 이력이다. **최종 커밋의 실행 URL·판정·시험 수는 [PR #2 본문](https://github.com/feed-mina/flow_fileupload_AI/pull/2)과 [Checks](https://github.com/feed-mina/flow_fileupload_AI/pull/2/checks)를 따른다.** 새 실행을 이전 실패 기록과 혼동하지 않는다.

현재 시험 구성: 서버 24개(정책 8, 업로드 서비스 2, 업로드 API·파일·HTTP 경계 14), Node API 6개, Chromium 기반 Chrome 5개, 별도의 실제 두 JVM 재시작 T01~T04. HTTP 초과 응답에는 413뿐 아니라 `UPLOAD_SIZE_LIMIT` JSON 코드도 확인한다. Origin 거부 응답은 requestId와 no-store를 포함하며, DB 접근·트랜잭션 실패는 503으로 분류한다. CI의 외부 폰트·시스템 패키지 설치 지연으로 runner 기본 Chrome 채널을 사용하도록 변경했다. 시험을 건너뛰거나 모킹으로 바꾸지 않았다.

실행 위치·명령: 저장소 루트 `mvn -B -ntp -f backend/pom.xml verify`, `node scripts/verify-restart.mjs`; frontend에서 `npm test`, `npm run build`, `npm run test:e2e`. DB·경로는 격리된 CI/Testcontainers 환경이며 실제 사용자 파일은 사용하지 않는다. 아티팩트 `policy-test-evidence`의 JUnit·재시작 JSON/로그·브라우저 보고서를 함께 확인한다.

아래는 PR #1의 기존 기록이다.

작성일: 2026-10-23. 이 문서는 실제 실행과 계획을 구분합니다.

| 검증 | 상태 | 근거 |
|---|---|---|
| Node API 요청·오류 처리 | 로컬 PASS 4/4 | `node --test frontend/src/lib/api.test.js`, exit 0 |
| 서버·PostgreSQL 통합 | CI PASS 6/6, skip 0 | `mvn -f backend/pom.xml verify`, Testcontainers |
| T01~T04·실제 Java 재시작 | CI PASS T01~T04 | `scripts/verify-restart.mjs`, 두 JVM + 동일 DB |
| 브라우저·실패·모바일 키보드 | CI PASS 4/4 | Playwright Chromium, retry 0 |
| 프론트 production build | CI PASS | Vite build |
| Docker 구성 문법 | CI PASS | `docker compose config --quiet` |
| 실제 Docker 이미지 빌드·Compose 실행 | 미실행 | 로컬 Docker 엔진 연결 불가 |

검증 실행: [Actions #2](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39890208527), 앱 구현 커밋 `d1167bd2e4748107ab699fabf0d55d7e3271bd9d`, 2026-10-23 02:33~02:35 UTC. 모든 검사 단계가 success였습니다. CI에서도 Node API 시험 4/4가 통과했습니다. 이후 커밋에는 이 실행에서 생성한 `package-lock.json`을 포함하고 CI 설치를 `npm ci`로 고정했습니다. 최신 커밋의 재검증은 [PR #1의 Checks](https://github.com/feed-mina/flow_fileupload_AI/pull/1/checks)에서 확인합니다.

실제 브라우저 시험은 최초 7개 미선택 → exe 선택·새로고침 유지 → 해제 → sh 추가·재조회 유지, 저장 503 시 이전 값·수정 잠금·재조회, 중복 409 시 입력 보존, 390px 화면에서 키보드 저장·가로 넘침 없음의 네 경로입니다. 장애·모바일 세 경로는 API 응답을 모킹하며, 첫 경로는 실제 서버·별도 PostgreSQL을 사용합니다. T03은 별도 스크립트에서 실제 서버 프로세스를 재기동합니다.

로컬에서는 Java/Node는 사용할 수 있으나 Docker 엔진이 연결되지 않았고 npm registry DNS 조회도 실패했습니다. PostgreSQL 시험을 메모리 DB로 대체하여 통과했다고 표기하지 않았습니다. 실제 Docker 이미지 빌드·Compose 기동과 컨테이너 재생성은 실행하지 않았습니다.

빌드는 성공했지만 Ant Design 번들의 단일 JS 청크가 587.78KB(gzip 190.71KB)로 Vite의 500KB 권고를 넘었습니다. 모듈의 `use client` 지시문 및 Actions 런타임 관련 경고도 있었으며 실패로 숨기지 않았습니다. 코드 분할과 Actions 메이저 갱신은 후속 최적화 항목입니다.

GitHub Actions의 `policy-test-evidence` 아티팩트에는 JUnit 보고서, 재시작 JSON/서버 로그, Playwright 보고서가 포함됩니다. 서버 시험의 DB 초기화는 Testcontainers가 생성한 격리 DB에서만 수행합니다. 브라우저 시험과 재시작 시험은 각각 별도 DB를 사용합니다. 현재 검사에는 자동 재시도가 없습니다.


## 반복 실행에서 발견한 브라우저 시험 문제

`66703ad2e7c5bedf55d596117485e7558fc0ade4`의 [반복 실행](https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39902659537)은 서버 30·Node 10·재시작은 통과했지만 브라우저 9/11에서 멈췄다. 두 실패 모두 이미 한 번 누른 로딩 버튼을 정확한 접근 가능한 이름으로 다시 찾는 단계다. 화면 상태 스냅샷과 pageerror 진단을 추가하고, 버튼의 실제 표시 문구로 찾은 뒤 활성화 상태를 확인하도록 보완했다. 요청 수·404 미확정·실제 저장·삭제 검증과 무재시도 설정은 유지한다. 최종 실행의 통과 여부는 PR #3 본문·Checks를 따른다.
