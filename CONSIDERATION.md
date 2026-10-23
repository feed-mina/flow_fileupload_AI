# 파일 업로드 과제 — 고려사항과 판단 근거

작성일: 2026-10-23 (한국 시간) · 기준: main `294855bc7afbf5a5990da3837bca64a6f213eca4`

**핵심: 저장된 확장자 정책을 서버에서 강제하고, 저장 성공·실패·결과 미확인을 구분한다.**

첨부 `과제_파일업로드_AI개발(3).md`의 3번 고려사항을 기준으로 기존 docs와 실제 코드를 대조했다. 앞서 제공된 (2) 사본과 내용이 동일함을 확인했다. REQ(Requirement)는 요구사항, DEC(Decision)는 설계 결정이다. 아래의 ‘구현’은 표시한 범위만 뜻하며, 시험 환경과 운영의 한계를 함께 적었다. 세부 항목 ID와 변경 이력은 [주석 가이드](docs/IMPLEMENTATION_GUIDE_ANNOTATED.md)를 따른다. 원본 가이드의 과거 초안보다 현재 코드와 [배포 후 점검 기록](docs/POST_DEPLOYMENT_CHECKS.md)을 우선한다.

## 1. 과제 범위와 선택

| 구분 | 선택과 이유 | 현재 상태 |
|---|---|---|
| 필수 기능 | 고정 7종 선택·해제, 커스텀 추가·삭제·중복 방지, DB 저장, 실제 파일 업로드 차단 | 구현·시험 기록 있음 |
| 기술 | Spring Boot·React·PostgreSQL·Docker. 화면, 서버, 저장소의 책임을 나누고 실제 DB로 검증 | 구현 |
| 정책 범위 | 모든 방문자가 공유하는 정책 1개. 작은 과제 범위에 맞추되 화면에서 공유임을 알림 | 사용자별 정책·편집 인가 미구현 |
| 배포 | Vercel 화면의 /api를 Hostinger 서버로 연결. DB와 파일은 Hostinger에 보관 | 공개 시험 11개 통과 기록 있음 |
| AI 활용 | 요구 분해·구현·검증·수정에 AI 사용. 서비스에 AI 모델을 넣는 것은 요구사항이 아님 | [입력·도구·채택 기록](PROMPT_LOG.md) |

## 2. 검증·보안 관점 — 과제 3-1

| 문제 / 질문 | 판단과 이유 | 구현·검증 및 한계 | 관련 항목·근거 |
|---|---|---|---|
| 확장자만으로 안전한가? | 필수 기능을 완성한 뒤, 실제 내용 검사·악성코드 검사는 확장자 필터링보다 상위의 확장 범위로 판단해 구현하지 않았다. 확장자 차단만으로 파일 안전성을 보장할 수 없으므로, 저장 파일은 서버에서 실행하거나 직접 공개하지 않는다 | 운영 내용 차단·악성코드 검사는 미구현. 시험용 내용 진단과 구분 | DEC-11, BE-S06 · [UploadService](backend/src/main/java/dev/mina/flow/upload/UploadService.java) · [내용 진단 시험](backend/src/test/java/dev/mina/flow/upload/ContentExtensionMatrixTest.java) |
| 실제 내용·이름이 다르면? | 파일의 확장자만 바꿔 필터링을 우회할 수 있는지와 실제 내용을 어디까지 판별할 수 있는지 확인하기 위해 시험했다. PDF/PNG/GIF 샘플과 Tika 등록 단일 ASCII 확장자 이름 1,284개를 진단하고, 판별할 수 없는 경우는 UNVERIFIABLE로 구분했다 | 시험 결과를 운영 업로드 차단에는 적용하지 않았다. PNG 내용을 다른 이름으로 바꾼 시험은 모든 형식의 유효한 샘플 시험이 아니다. MATCH 1·MISMATCH 1,206·미판정 77 | BE-S06, T19 · [시험 결과](docs/TEST_RESULTS.md) |
| MIME 타입을 위조하면? | MIME(Multipurpose Internet Mail Extensions, 파일 내용 유형) 헤더를 허용 근거로 사용하지 않음 | 확장자·파일명·크기·저장 경로 검증. MIME이나 확장자만으로 내부 내용의 안전성을 판단하지 않음 | BE-S05·06 · [UploadController](backend/src/main/java/dev/mina/flow/upload/UploadController.java) |
| 대소문자·유니코드 입력은? | 정책 입력 앞뒤 공백과 선행 점 1개를 제거하고 원문 ASCII 영문·숫자 1~20자를 검사한 뒤 소문자화. 서로 다른 입력을 같은 키로 정규화 | `.PDF`와 `pdf`는 같은 값. 유니코드를 먼저 소문자화해 영문으로 바뀌는 우회를 방지 | DEC-02, REQ-05·09 · [PolicyService](backend/src/main/java/dev/mina/flow/policy/PolicyService.java) |
| 이중·복합 확장자는? | 마지막 점 뒤만 비교. `file.exe.txt`는 txt, `archive.tar.gz`는 gz. 규칙을 단순하고 예측 가능하게 유지 | 커스텀 `tar.gz`는 거부. 중간 확장자·압축 파일 내부 검사는 없음 | DEC-04·05 · [FilenameRules](backend/src/main/java/dev/mina/flow/upload/FilenameRules.java) |
| 확장자가 없거나 숨김 이름·긴 이름이면? | `README`, `.env`, `file.`은 서로 다른 사유로 거부. 이름은 UTF-8 255바이트 이하 | `.env.txt`처럼 마지막 확장자가 있는 이름은 동일한 마지막 확장자 규칙 적용. 글자 수와 바이트 수는 다름 | DEC-06·08 · [FilenameRules](backend/src/main/java/dev/mina/flow/upload/FilenameRules.java) |
| 화면 검증을 우회하면? | 클라이언트는 사전 안내, 서버가 최종 판정. DB 제약으로 잘못된 데이터도 방어 | 직접 HTTP 요청·차단 확장자 시험 있음. SQL은 파라미터 바인딩 사용 | REQ-10, BE-S01 · [업로드 시험](backend/src/test/java/dev/mina/flow/upload/UploadApiTest.java) · [PolicyRepository](backend/src/main/java/dev/mina/flow/policy/PolicyRepository.java) |
| 크기·개수·빈 파일 제한은? | 요청당 1개, 파일당 10 MiB(10,485,760바이트), 요청 전체 12 MiB, 0바이트 거부. 과제의 추가 설계값이며 보편적인 안전 기준은 아님 | 서버·스트림 복사·프록시 제한. 공개 Vercel 경유 10 MiB−1/정확히/+1은 201/201/413 기록 | DEC-07·08 · [application.yml](backend/src/main/resources/application.yml) · [공개 결과](docs/evidence/public-deployment-2026-10-23.json) |
| 원본 이름으로 저장해도 되는가? | 원본 이름은 표시용 메타데이터. 실제 경로는 UUID(Universally Unique Identifier, 범용 고유 식별자) 기반 `.bin` | 경로·제어문자 거부, 웹 공개 경로 밖에 저장. 같은 이름 덮어쓰기 방지. 다운로드 기능은 없음 | DEC-09, BE-S03·04 · [UploadStorage](backend/src/main/java/dev/mina/flow/upload/UploadStorage.java) |
| 쿠키 요청을 다른 사이트가 위조하면? | 세션 쿠키와 CSRF(Cross-Site Request Forgery, 사이트 간 요청 위조) 토큰, 허용 출처 검사 | 공개 시험에서 토큰 없는 변경403·정상 변경 성공, Secure/HttpOnly/Lax 확인. 인증과 인가는 별개. 공개 데모 로그인은 미설정 | BE-S12 · [SessionSecurity](backend/src/main/java/dev/mina/flow/security/SessionSecurity.java) |
| DB 계정이 과도한 권한을 가지면? | 마이그레이션 관리자와 실행 앱 계정을 분리. 앱은 필요한 조회·변경만 허용 | DDL·역할 생성·감사 변경/삭제 거부를 격리 DB에서 시험. 감사 조회도 앱에 GRANT하지 않음 | DB-S04 · [V4 권한](backend/src/main/resources/db/migration/V4__least_privilege_application_role.sql) |

## 3. 정책·데이터 관점 — 과제 3-2

| 문제 / 질문 | 판단과 이유 | 구현·검증 및 한계 | 관련 항목·근거 |
|---|---|---|---|
| 고정·커스텀에 exe가 겹치면? | 고정 영역에서 선택하도록 안내하고 커스텀 추가는 거부. 사용자가 원치 않는 고정 차단을 자동으로 켜지 않음 | 서버409와 DB `ck_kind_extension`으로 분리 | DEC-03, REQ-04·09 · [V1](backend/src/main/resources/db/migration/V1__policy_schema.sql) |
| 중복·200개 동시 추가는? | `(policy_id, extension)` UNIQUE. 정책 부모 행을 잠근 상태에서 중복→개수 검사→삽입→버전 증가 | 199개에서 서로 다른 동시 추가는 최종200개. 200개 제한은 DB CHECK/트리거가 아닌 서버 트랜잭션 규칙이므로 직접 DB 쓰기는 우회 가능 | DB-P01~03, REQ-07·09 · [PolicyService](backend/src/main/java/dev/mina/flow/policy/PolicyService.java) |
| 20자·200개의 근거는? | 첨부 과제의 명시 제한을 그대로 채택. 보안/성능 측정으로 새로 산출한 값이 아님 | 20/21자, 200/201 경계·중복 우선 오류. 화면에 개수·길이·필드 오류 표시, 200개이면 추가 비활성 | REQ-05·07 · [App](frontend/src/App.jsx) · [정책 시험](backend/src/test/java/dev/mina/flow/policy/PolicyApiTest.java) |
| 변경 이력이 필요한가? | 장애·충돌 설명에 필요해 정책 변경·버전·감사를 같은 트랜잭션으로 저장 | 확장자·동작·변경 후 활성값·시간·버전 저장. 인증된 변경자·이전 값·감사 조회 UI는 없음. 삭제의 enabled=false는 사건 값이지 삭제 전 값이 아님 | DB-U04, DB-O04 부분 구현 · [V3](backend/src/main/resources/db/migration/V3__ux_audit_and_upload_lookup.sql) |
| 수백 개 조회·저장 성능은? | 최대207행을 먼저 실측. UNIQUE 인덱스는 중복 방지에 사용하며 불필요한 추가 인덱스를 만들지 않음 | 격리 CI의 커스텀200개에서 읽기 p95 1.808ms·쓰기7.684ms. p95는 측정95%가 그 시간 이내. 실행 계획은 Seq Scan+정렬. 공개 서버·네트워크 성능 수치가 아님 | DB-P06 · [DB 시험](backend/src/test/java/dev/mina/flow/policy/DatabaseOperationsTest.java) · [측정 조건](docs/TEST_RESULTS.md) |
| 어떤 테이블·인덱스가 실제로 있는가? | 정책 범위·항목·감사, 업로드 메타데이터를 분리 | PK/UNIQUE 자동 인덱스와 FK를 구분. 조회용 별도 수동 인덱스는 없음 | REQ-13 · [README table_schema](README.md#table_schema) |

## 4. UX·예외 관점 — 과제 3-3

UX(User Experience)는 사용 경험이다. 성공처럼 보였지만 저장되지 않는 상황과, 실제 저장됐는데 응답을 못 받은 상황을 나눴다.

| 문제 / 질문 | 판단과 이유 | 구현·검증 및 한계 | 관련 항목·근거 |
|---|---|---|---|
| 무엇이 왜 차단됐는지? | 오류 코드·한국어 사유·적용 정책 버전·추적 번호로 설명. 전송 전 화면 안내는 최종 판정이 아님 | 허용 확장자도 안전성을 보장하지 않는다고 화면에 명시 | REQ-11, FE-P04 · [UploadPanel](frontend/src/UploadPanel.jsx) |
| 로딩·조회 실패는? | 진행 상태·중복 조작 방지·수동 재조회 제공. 조회 실패를 빈 목록으로 표시하지 않음 | 숨김 화면이 아닌 동안 60초 기준 재조회, 포커스 복귀 시 오래된 값 갱신. 창을 자동 종료하지 않음 | FE-U01~06, FE-P03 · [usePolicy](frontend/src/usePolicy.js) |
| 정책 저장에 실패하면? | 성공 응답 이후 화면 확정. 입력은 보존하고 저장 불명·버전 충돌이면 재조회 | 충돌은 최신 정책을 보여주고 사용자가 다시 검토. 사용자의 변경을 자동 재적용하지 않음 | DB-U02, FE-U03 · [App](frontend/src/App.jsx) · [usePolicy](frontend/src/usePolicy.js) |
| 업로드 응답이 끊기면? | 요청 UUID를 sessionStorage에 보관하고 결과만 조회. 자동 재전송으로 중복 저장하지 않음 | 404는 아직 처리 중일 수도 있어 실패 확정이 아님. 완료되면 파일 입력 초기화. 동일 요청 ID는 DB UNIQUE로 방어 | BE-U05·06, FE-O03 · [UploadService](backend/src/main/java/dev/mina/flow/upload/UploadService.java) · [UploadPanel](frontend/src/UploadPanel.jsx) |
| 모바일·접근성은? | 기존 라벨·상태 안내·키보드 조작 유지, FE-U07 신규 확장은 사용자 지정 제외 | 390×844·200개 목록의 가로 넘침/삭제/초기화 시험 있음. 모바일 성능은 API 모킹·3회 소표본이며 실기기 지연 보장은 아님 | FE-O05, FE-U07 · [브라우저 시험](frontend/e2e/policy.spec.js) · [시험 조건](docs/TEST_RESULTS.md) |

## 5. 운영 관점 — 과제 3-4와 추가 발견

| 문제 / 질문 | 판단과 이유 | 구현·검증 및 한계 | 관련 항목·근거 |
|---|---|---|---|
| 새로고침·두 방문자 편집 충돌은? | DB 정책 버전과 If-Match로 오래된 변경 거부. 부모 잠금으로 저장 순서 보장 | 재조회는 REPEATABLE_READ로 일관된 스냅샷. IP/사용자 ID만 붙여서는 충돌 방지가 안 됨. IP는 공유·변동돼 소유권 기준으로 부적합 | DEC-01, DB-P03 · [PolicyService](backend/src/main/java/dev/mina/flow/policy/PolicyService.java) |
| 업로드 중 정책이 바뀌면? | 검증 시작 때의 커밋된 정책을 한 번 읽고 해당 파일에 적용·기록 | 다음 요청부터 새 정책 적용. 이미 허용된 파일을 소급 취소하지 않음 | DEC-10 · [UploadService](backend/src/main/java/dev/mina/flow/upload/UploadService.java) |
| 파일과 DB 중 하나만 저장되면? | 임시 파일→PENDING 메타데이터→파일 이동→READY→DB 커밋 뒤 성공 응답. 롤백 시 파일 정리 | DB·파일시스템은 하나의 원자적 트랜잭션이 아님. 강제 종료/커밋 불명은 대조 필요. 정상 `.bin`을 추측해 자동 삭제하지 않음 | BE-O01·02 · [장애 대조](docs/DEC_IMPLEMENTATION.md) |
| 강제 종료한 임시 파일은? | 단일 인스턴스에서 24시간 지난 `pending-*.tmp`를 시간당 최대100개 정리 | 활성 파일·심볼릭 링크·정상 `.bin` 제외. 정상 업로드 보관 정책이나 다중 인스턴스 청소 보장이 아님 | BE-O02 · [TemporaryUploadJanitor](backend/src/main/java/dev/mina/flow/operations/TemporaryUploadJanitor.java) |
| 무엇을 로그로 남기는가? | 요청 추적 ID·분류 경로·메서드·상태·결과·정책 버전·크기·처리시간을 JSON 기록 | 원문 파일명·IP·쿠키·토큰·쿼리 비기록. 알림 대시보드·로그 순환/보관 자동화는 없음 | BE-O05, DB-O05 · [RequestLogFilter](backend/src/main/java/dev/mina/flow/operations/RequestLogFilter.java) |
| 과도한 요청·디스크 고갈은? | 연결 주소 기준 변경120회/분·동시 업로드4개, 429와 재시도 안내 | 단일 인스턴스 메모리 제한. 프록시가 보는 주소에 따라 여러 사용자가 제한을 공유할 수 있음. 정상 파일 전체 총량·보관 기간은 미구현이므로 디스크 고갈을 완전히 막지 못함 | BE-O06, DEC-13 · [TrafficGuard](backend/src/main/java/dev/mina/flow/operations/TrafficGuard.java) |
| 사용자별 정책으로 확장하려면? | 인증된 사용자/조직 ID와 정책 소유권·쿼리 범위를 함께 바꿔야 함 | `policy_id=1` 가정·단일 행 CHECK·API·감사 주체·권한 시험을 함께 변경해야 함. 현재는 설계 고려만 함 | DEC-01, BE-S11 · [정책 스키마](backend/src/main/resources/db/migration/V1__policy_schema.sql) |
| 화이트리스트로 바꾸려면? | 현재는 등록 확장자를 막는 차단목록. 허용목록은 정책 모드와 기본 거부 기준을 별도 결정해야 함 | 미구현. 기존 데이터 의미·화면 문구·업로드 판정·미등록 확장자 시험을 함께 이행해야 하며 단순 반전으로 설명하지 않음 | REQ-14 · [주석 가이드](docs/IMPLEMENTATION_GUIDE_ANNOTATED.md) |
| 분리 배포에서 재현·복귀는? | Vercel rewrite와 실제 Traefik/Nginx 구성을 기록하고 기본 이미지를 digest로 고정 | 서버 현재 이미지 ID·현재 Vercel Source 대조·실제 버전 복귀는 미확인. main 갱신만으로 Hostinger 실행 버전이 같다고 단정하지 않음 | DEC-12, DP-O03·04 · [배포 후 점검](docs/POST_DEPLOYMENT_CHECKS.md) |

## 6. 의도적 제외와 검증 경계

| 항목 | 상태·이유 | 다음에 완료하려면 |
|---|---|---|
| 정상 파일 보관·총량 DEC-13 | 운영 정책으로 사용자 보류 결정 | 기간·용량·삭제 승인 기준 결정 후 구현 |
| 정책 편집 인가·사용자 격리 BE-S11 | 공개 공유 데모 범위로 사용자 보류 결정 | 인증 주체별 소유권·접근 제어·회귀 시험 |
| DB/파일 동시 백업·복원 DB-O03 / T25 | 절차만 있으며 운영 컨테이너 영속성·백업·복원 시험은 사용자가 제외 | 일관된 백업과 별도 볼륨 복원·대조 |
| 감사/로그 cron DB-O05 | 매일02시·일요일03시 안내서만 작성. 예약 작업 설치 안 함 | 보관 기준 확정·설치·작업 결과 확인 |
| 신규 자동복구 BE-O07 | 기능 추가 안 함. 대응 절차만 기록 | 장애 기준·readiness·복구 방식 결정 |
| FE-U07 신규 접근성 확장 | 이번 추가 구현 범위 제외. 기존 접근성을 제거한 것은 아님 | 키보드·보조기기 평가 후 보완 |

[운영 예약 안내](docs/OPERATIONS_SCHEDULE_GUIDE.md) · [배포 준비](docs/HOSTINGER_VERCEL_DEPLOYMENT.md) · [제출 점검표](docs/SUBMISSION_CHECKLIST.md)

## 7. 확인한 증거와 읽는 순서

1. [README](README.md): 공개 주소·실행법·실제 스키마.
2. [AI 활용 기록](PROMPT_LOG.md): 시간 순 입력, 수정 요청, 채택·수정·제외 이유.
3. [시험 결과](docs/TEST_RESULTS.md): 격리 CI 서버40·화면 API10·브라우저16·설정 도구4개와 측정 조건.
4. [공개 시험 결과](docs/POST_DEPLOYMENT_CHECKS.md): 2026-10-23 실제 공개 경로11개 통과 기록. 이번 문서 작업에서 공개 쓰기 시험을 다시 실행하지는 않았다.

‘고려함’, ‘코드 구현’, ‘격리 환경 시험’, ‘공개 환경 확인’을 같은 완료로 세지 않는다. 이 문서는 확인 가능한 코드·기록에 근거한 정리이며 지원자의 개인 경험이나 최종 회고를 대신하지 않는다.
