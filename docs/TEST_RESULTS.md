# T01~T04 시험 결과

작성일: 2026-10-23. 이 문서는 실제 실행과 계획을 구분합니다.

| 검증 | 상태 | 근거 |
|---|---|---|
| Node API 요청·오류 처리 | 로컬 PASS 4/4 | `node --test frontend/src/lib/api.test.js`, exit 0 |
| 서버·PostgreSQL 통합 | CI 실행 대기 | `mvn -f backend/pom.xml verify`, Testcontainers |
| T01~T04·실제 Java 재시작 | CI 실행 대기 | `scripts/verify-restart.mjs`, 두 JVM + 동일 DB |
| 브라우저·실패·모바일 키보드 | CI 실행 대기 | Playwright 4개 시험 |
| 프론트 production build | CI 실행 대기 | Vite build |
| Docker 구성 문법 | CI 실행 대기 | `docker compose config --quiet` |
| 실제 Docker 이미지 빌드·Compose 실행 | 미실행 | 로컬 Docker 엔진 연결 불가 |

로컬에서는 Java/Node는 사용할 수 있으나 Docker 엔진이 연결되지 않았고 npm registry DNS 조회도 실패했습니다. PostgreSQL 시험을 메모리 DB로 대체하여 통과했다고 표기하지 않았습니다. CI가 완료되면 실행 URL과 결과를 이 문서에 추가합니다.

GitHub Actions의 `policy-test-evidence` 아티팩트에는 JUnit 보고서, 재시작 JSON/서버 로그, Playwright 보고서가 포함됩니다. 서버 시험의 DB 초기화는 Testcontainers가 생성한 격리 DB에서만 수행합니다. 브라우저 시험과 재시작 시험은 각각 별도 DB를 사용합니다. 현재 검사에는 자동 재시도가 없습니다.
