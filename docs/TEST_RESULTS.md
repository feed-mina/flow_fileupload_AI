# T01~T04 시험 결과

## DEC 후속 검증 — PR #2

- 로컬: Node API 6개 PASS. 삭제 요청·multipart 헤더·READY 판정 포함.
- 새 서버 통합·파일 장애·경합·HTTP 크기 경계 및 브라우저 시험: CI 실행 중. 기존 PR #1 통과 결과와 혼동하지 않는다.
- 실제 Docker 이미지 빌드·Compose 기동·공개 배포·컨테이너 재생성: 미실행.
- 실행: https://github.com/feed-mina/flow_fileupload_AI/actions/runs/39896452446

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
