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
