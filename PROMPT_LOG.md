# AI 활용 기록 — 프롬프트·스킬·플러그인·검증

작성일: 2026-10-23 (한국 시간) · 기준: main `294855bc7afbf5a5990da3837bca64a6f213eca4`

**AI에 요구사항을 단계별로 맡기고, 생성 결과를 실제 DB·HTTP·브라우저 시험으로 확인하며 수정했다.**

이 문서는 과제 4-1~4-3 제출용 요약이다. [전체 질문·진행 로그](docs/플로우_과제_대화로그.txt)와 [기존 개발 기록](docs/PROMPT_LOG.md)을 근거로 작성했다. 대부분 개별 시각은 없어 **2026-10-23 안의 대화 순서**로 기록한다. 아래 입력은 원문 발췌 또는 명시한 요약이며, 실제로 쓰지 않은 재질문을 만들어 넣지 않았다. 비밀번호·쿠키·토큰·내부 추론은 포함하지 않는다.

## 1. 시간 순 입력과 반복 개선

| 순서 | 사용자 입력 / 의도 | AI의 작업·결과 | 수정·검증과 남은 범위 |
|---|---|---|---|
| 01 조사 | 원문 발췌: “플로우 회사와 내가 지원한 엔터프라이즈 개발자 직군이 어떤일을 하는지 와 어떤 기술 스택을 사용하는지 … 조사해줘.” 직무와 과제의 연결 파악 | 공식 회사·직무 자료와 과제를 대조하고 필수 동작·제출물을 정리 | 캡처 정밀 판독 실패를 숨기지 않고 공식 자료로 교차 확인. 공식 직무에 없는 프레임워크를 회사 필수 스택으로 단정하지 않음 |
| 02 범위 고정 | 원문 발췌: “백앤드는 SpringBoot, 프론트는 React, DB는 PostgreSQL, 배포는 Docker, 저장소는 Git을 사용할 예정이다.” | 기술 선택과 저장소를 고정하고 REQ·DEC·분야별 점검 항목으로 분해 | 권장 초안을 확정 요구사항과 구분. UI 참고 라이브러리를 실제 도입 목록과 구분 |
| 03 최초 구현 | 기존 기록 요약: T01~T04 최초 상태→선택 저장→재시작 유지→커스텀 추가를 구현하고 PR 생성 | 정책 SQL·Flyway·API·React 화면·CI 구성. [PR #1](https://github.com/feed-mina/flow_fileupload_AI/pull/1) | exe=false만 확인하면 초기화 오류를 놓칠 수 있어 scr=true·버전·행 ID도 함께 재시작 검증 |
| 04 업로드 구현 | 원문 발췌: “3-1 검증/보안관점 DEC에서 권장초안에 적힌 대로 참고하여 과제를 진행합니다. 이후 PR까지 진행해주세요.” | 마지막 확장자 검증·차단·UUID 저장·커스텀 삭제·HTTP 경계 시험. [PR #2](https://github.com/feed-mina/flow_fileupload_AI/pull/2) | 413 대신 연결 끊김, Vite Host 변경을 실제 시험에서 발견. 소비 한도·프록시 설정을 수정하고 보안 검사는 유지 |
| 05 UX 보완 | 원문 발췌: “DB와 서버는 필수/권장 … FE-U07 사항을 빼고 구현 후 PR까지 진행해주세요” | 감사와 정책을 한 트랜잭션으로 저장, 미확인 업로드 결과 조회, 입력 근처 오류·로딩 안내. [PR #3](https://github.com/feed-mina/flow_fileupload_AI/pull/3) | 업로드를 무조건 재전송하지 않음. 버튼 접근 이름에 loading이 붙어 반복 시험이 실패한 원인을 찾아 locator 수정. 서버30·화면 API10·브라우저11 당시 기록 |
| 06 설명 가능한 결과 | 원문 발췌: “구현핵심을 1줄로 요약하고 진행과정을 3줄로 … 오른쪽은 클릭형으로 … 왼쪽에서는 관련된 소스” | PR1~3 HTML·MD 보고서와 GitHub 소스 링크 작성 | 390px에서 기존 보고서가 넘친 문제 보완. Chrome 클릭·키보드·작은 화면 검증. [보고서 목록](docs/reports/README.md) |
| 07 가이드 추적성 | 원문 발췌: “DEC 표서 이유 * 한계 다음 열에 … 관련된 REQ 를 추가” → “main기준으로 … 구현/미구현도 같이 표시” | REQ·DEC·고려사항·시험을 코드 근거와 연결, 상태 표시 | 코드 존재와 공개 배포 확인을 분리. 전용 DEC가 없는 항목도 억지로 일대일 연결하지 않음 |
| 08 구현 범위 재결정 | 원문 발췌: “DEC12 … 가변 태그에서 고정 태그로 변경” / “DBP06 DB테스트로 부히/지연 측정” / “BE-S12 … CSRF방어 구현 필요” | 최소 권한·이미지 digest 고정·세션/CSRF·임시 파일 청소·구조화 로그·요청 제한·모바일 시험. [PR #5](https://github.com/feed-mina/flow_fileupload_AI/pull/5) | 사용자 지정 DEC-13·BE-S11·BE-O07 보류, cron은 안내만. 모든 확장자 내용을 증명할 수 없어 일치·불일치·미판정으로 축소해 정직하게 기록 |
| 09 배포 | 별도 배포 대화 원문 발췌: “호스팅어로 배포해줘” → “vercel로 프론트앤드 배포하고 싶어요” → “지금까지 한 과정을 깃허브에 docs에 올려주세요” | Hostinger 서버/DB와 Vercel 화면, 외부 rewrite 연결 기록 | 계획 Caddy와 실제 Traefik/Nginx 구성을 구분. [배포 이력](docs/DEPLOYMENT_PROGRESS_2026-10-23.md). 별도 대화의 원문 전체는 이 저장소 로그에 있다고 주장하지 않음 |
| 10 배포 후 점검 | 원문: “영속성·백업·복원 시험 을 빼고 나머지 6개 사항 후 PR을 올려주세요” | 실제 공개 화면 연결·정책·세션/CSRF·업로드·크기 경계, 배포 재현/복귀 절차·제출 문서. [PR #6](https://github.com/feed-mina/flow_fileupload_AI/pull/6) | 로컬 DNS 실패를 통과로 취급하지 않고 Actions에서 공개11개 검증. 정책 원복, 시험 파일3개 약20MiB 잔존 기록. 실제 서버 복귀·운영 백업 시험 안 함 |
| 11 원문 보존 | 원문: “플로우 과제 대화로그도 main에 올려ㅝ” | 공개 가능한 로그를 docs에 게시하고 로컬 링크를 GitHub 주소로 정리 | 당시 패턴 점검에서 비밀값은 발견되지 않았다는 기록. 이를 모든 과거 이력의 비밀 검사 완료로 확대하지 않음 |
| 12 제출 문서 정리 | 현재 원문: “과제지시사항을 읽고 docs 문서를 고려한 후 고려사항 문제를 적는 CONSIDERATION.md 와 AI활용기록 PROMPT_LOG.md (프롬프트 스킬/플러그인) 를 작성하고 리드미에 table_schema 확장자 정책 저장하는 테이블 정의 (컬럼/타입/제약/인덱스) 를 업데이트 하자” | 첨부 (2)·(3) 동일 내용 확인, main docs·V1~V4·정책 코드를 대조. 루트 제출 문서와 README 표 작성 | 200개는 DB CHECK가 아닌 서버 잠금·개수 검사임을 명시. 과제 표기 CONSIDERATIONS.md에서도 본문 연결. 이번 변경은 문서이며 공개 쓰기 시험을 재실행하지 않음 |

## 2. 사용한 스킬

스킬은 AI 작업 지침이다. 설치되어 있다는 이유만으로 사용했다고 기록하지 않았다. ‘이전 기록’은 위 docs의 당시 실행 기록이며, 이번 문서 작성에서 다시 실행했다는 뜻은 아니다.

| 스킬 | 어디에·왜 사용했는가 | 확인 근거 |
|---|---|---|
| analyze-github-projects | 가이드 계획과 main 코드·PR·시험 근거를 대조하고 구현/미구현을 분리 | 이전 로그와 이번 문서 작업에서 실제 지침을 읽고 적용 |
| PostgreSQL 모범 사례 | FK 인덱스·짧은 트랜잭션·권한·실행 계획을 검토 | [기존 개발 기록](docs/PROMPT_LOG.md) UX·PR5 항목, V3/V4와 DB 시험 |
| screen-code-handover | 화면→함수→API→DB를 쉬운 설명과 클릭형 보고서로 연결 | [전체 로그](docs/플로우_과제_대화로그.txt)·[PR1~3 보고서](docs/reports/README.md). 당시 참조 문서 일부 읽기 실패도 기록 |
| Library | 첨부·대화 로그·보고서 파일 읽기/저장에 사용 | 이전 전체 로그의 조사·보고서 단계. 이번 첨부는 제공된 로컬 사본을 읽음 |
| Browser/control-browser | Actions 상세 화면 확인을 시도 | 이전 로그에 로그인 제한으로 상세 확인을 종료한 사실 기록. 브라우저 시도 자체를 시험 통과 근거로 사용하지 않음 |

easy-guide·fix-guide라는 **이름만 보고 설치·실행 완료로 추가하지 않았다.** 관련 리서치 산출물은 [docs/reports](docs/reports/README.md)에 있으며, 실제 로드 여부가 확인되지 않는 스킬은 사용 목록에 추정하지 않는다.

## 3. 플러그인·연결 도구·에이전트

MCP(Model Context Protocol)는 AI가 외부 도구에 연결하는 공통 규약이다. 개발 보조 도구와 앱에 포함되는 라이브러리는 별도다.

| 도구 | 어디에·왜 사용했는가 | 범위·한계 |
|---|---|---|
| ChatGPT/Codex 코딩 에이전트 | 요구 분석·코드/시험/문서 초안·오류 수정에 사용 | 당시 모든 모델의 정확한 식별자는 기록에 없어 임의 기재하지 않음. 지원자의 개인 회고를 AI가 사실처럼 창작하지 않음 |
| GitHub 플러그인·GitHub API | main 파일·트리·PR·실행 근거 읽기와 요청한 저장소 변경 | 이번 작업은 연결된 GitHub 도구로 문서 읽기·반영. 인증정보는 기록하지 않음 |
| 파일 도구·터미널·Git | 파일 편집·차이·링크 검사, 기존 개발의 실행·버전 관리 | 이번 환경의 일반 Git clone은 네트워크/인증 제한으로 실패하여 연결된 GitHub 도구로 대체. clone 실패는 서비스 장애가 아님 |
| 웹 검색·페이지 열기 | 초기 회사 공식 자료, 기술 공식 문서 확인 | 이전 개발 기록 근거. 이번 스키마는 웹 추측 대신 실제 SQL 확인 |
| Browser 연결 | 상세 Actions 화면·배포 콘솔 작업 보조 | 로그인/권한/네트워크 제한은 당시 기록을 따름. 현재 사용 가능한 플러그인 전체가 사용 이력은 아님 |
| GitHub Actions | 격리 DB·브라우저·공개 배포 검증 실행과 증거 보관 | CI(Continuous Integration, 지속적 통합) 도구이며 AI 모델/플러그인이 아님 |

이번 문서 작업에서는 하위 에이전트·멀티에이전트·추가 플러그인을 사용하지 않았다. 과거 모든 세션의 실행 내역까지 단정하는 표현은 피한다.

## 4. 라이브러리·실행/시험 도구

| 라이브러리·도구 | 어디에·왜 사용했는가 | 근거 |
|---|---|---|
| React·Ant Design·Vite | 정책·업로드·세션 화면, UI 일관성, 빌드/개발 프록시 | [frontend/package.json](frontend/package.json)·[App.jsx](frontend/src/App.jsx) |
| Spring Boot·Spring Security·JDBC | API, 트랜잭션, 세션/CSRF, 파라미터 바인딩 SQL | [backend/pom.xml](backend/pom.xml)·[PolicyService](backend/src/main/java/dev/mina/flow/policy/PolicyService.java) |
| PostgreSQL·Flyway | 정책·감사·업로드 메타데이터와 재현 가능한 스키마 변경 | [V1~V4](backend/src/main/resources/db/migration) |
| Docker·Compose | 서버·DB·프록시 분리와 동일 구성 검증 | [compose.yml](compose.yml)·[실제 override](compose.vps-live.yml) |
| JUnit·Testcontainers | 실제 PostgreSQL로 제약·경합·롤백·권한 확인 | [서버 시험](backend/src/test/java/dev/mina/flow) |
| Playwright·Chromium·Node.js | 화면/API·공개 서비스·실제 프로세스 재시작 검사 | [브라우저 시험](frontend/e2e)·[scripts](scripts) |
| Apache Tika | 시험에서 내용 시그니처·확장자 대응 진단 | [ContentExtensionMatrixTest](backend/src/test/java/dev/mina/flow/upload/ContentExtensionMatrixTest.java). 운영 업로드 내용 차단 기능이 아님 |
| Nginx·Traefik·Vercel·Hostinger | 실제 프록시·HTTPS·화면·서버 배포 | [배포 이력](docs/DEPLOYMENT_PROGRESS_2026-10-23.md). Caddy는 계획 구성/검증, 실제 서비스 경로 아님 |

shadcn·dropzone·별도 전역 상태관리 라이브러리는 실제 앱에 도입하지 않았다. 초기화 때 업로드 디렉터리를 삭제하는 예제 로직도 채택하지 않았다.

## 5. AI 결과에서 채택·수정·버린 내용과 이유

다음은 기록에 근거한 **작업 회고**이며, 지원자가 직접 모든 오류를 발견했다는 의미는 아니다.

| 구분 | 제안·초기 결과 | 실제 판단과 이유 | 확인 근거 |
|---|---|---|---|
| 채택 | Controller→Service→Repository, Flyway SQL | 화면·서버·DB 책임을 나누고 같은 DB 구조를 재현 | [README](README.md)·[정책 코드](backend/src/main/java/dev/mina/flow/policy) |
| 채택 | 정책 변경·버전·감사를 함께 저장 | 감사만 남거나 정책만 저장되는 불일치 방지 | V3·감사 실패 롤백 시험 |
| 수정 | 입력을 먼저 소문자화하는 초기 순서 | 유니코드가 ASCII로 바뀔 수 있어 **원문 ASCII 검사 후** 소문자화 | [PolicyService.normalize](backend/src/main/java/dev/mina/flow/policy/PolicyService.java) |
| 수정 | 초과 업로드에서 연결 끊김 | JSON 413 안내가 필요해 Tomcat 소비 한도를12MiB로 조정. 제한을 무제한 해제하지 않음 | [시험 기록](docs/TEST_RESULTS.md) PR2 |
| 수정 | Vite 문자열 프록시 | Host 변경으로 Origin 검사가 실패해 `changeOrigin:false`로 수정. 보안 검사는 유지 | [vite.config.js](frontend/vite.config.js)·시험 기록 |
| 수정 | 로딩 버튼을 찾는 브라우저 시험 | loading 아이콘이 접근 이름에 들어가 반복 실행 실패. 표시 문구·활성 상태를 확인하도록 수정 | [PR3 기록](docs/플로우_과제_대화로그.txt)·[policy.spec.js](frontend/e2e/policy.spec.js) |
| 수정 | 모든 확장자 내용 일치/불일치 검사 요청 | 모든 형식을 증명할 수 없어 샘플·등록 이름 범위와 미판정을 공개. 운영 검사 완료로 확대하지 않음 | PR5·[시험 결과](docs/TEST_RESULTS.md) |
| 제외 | IP만으로 사용자별 정책 분리 | 공유·변동 IP는 소유권 기준이 아니며 인증 ID도 서버 소유권 검사 필요 | 사용자 질문과 DEC-01 분석 기록 |
| 제외 | 운영 정책까지 한꺼번에 구현 | 사용자 결정에 따라 보관/총량·인가/격리·cron 설치·신규 복구·FE-U07 확장 보류 | [고려사항](CONSIDERATION.md) |
| 제외 | 응답 유실 뒤 업로드 자동 재전송 | 실제 저장됐을 수 있어 중복 위험. requestId로 결과만 조회 | [UploadPanel](frontend/src/UploadPanel.jsx) |
| 제외 | 예제의 시작 시 저장 디렉터리 삭제 | 재시작 때 기존 파일이 지워지므로 사용하지 않음 | [README](README.md)·[UploadStorage](backend/src/main/java/dev/mina/flow/upload/UploadStorage.java) |

## 6. 사용자 결정과 AI 검증의 경계

- 사용자 결정: 기술 스택, FE-U07 제외, 고정 이미지/최소 권한/CSRF/로그 보완, 운영 기능 보류, Hostinger+Vercel, 영속성·백업·복원 시험 제외.
- AI/시험에서 확인한 수정: 입력 정규화 순서, 413 연결 끊김, 프록시 Host, 로딩 버튼 locator, 요청 builder 타입·이미지 digest 호환 문제. 이를 ‘지원자가 직접 발견했다’고 바꿔 쓰지 않았다.
- 검증 증거: [공개11개](docs/POST_DEPLOYMENT_CHECKS.md), [격리 회귀·측정 조건](docs/TEST_RESULTS.md). 이번 문서 편집은 기존 결과를 인용하며 앱 시험을 새로 통과했다고 주장하지 않는다.
- 아직 남음: 실제 운영 이미지 ID/복귀 확인, 보류 기능, 지원자 본인의 최종 회고. [제출 점검표](docs/SUBMISSION_CHECKLIST.md)의 질문은 지원자 경험으로 마무리한다.

기록에서 확인할 수 있는 판단을 먼저 작성했으며, AI가 수행한 일을 지원자 개인 성과로 바꾸거나 확인하지 않은 도구·시험·시각을 만들어 넣지 않았다.

## 7. 사용자와 판단·이유 개별 점검 — 2026-10-23

### 1차: 확장자만으로 안전한가?

- 사용자 요청: “판단과 이유 를 하나씩 나와 점검해서 문서를 수정하자.”
- AI 제안: 필수 기능을 우선 완성하고 내용 검사를 후속 확장으로 남겼다는 이유인지 확인했다.
- 사용자 확정 원문: “필수 기능을 완성 후 후속 확장은 확장자 필터링의 상위 확장 범위라 작업을 하지 않았습니다.”
- 반영: 필수 기능 완성 후 내용 검사·악성코드 검사를 확장자 필터링보다 상위의 확장 범위로 판단해 구현하지 않았다고 CONSIDERATION.md의 첫 판단 행을 수정했다. 향후 구현 일정이나 계획을 확정한 것으로 기록하지 않는다.
- 유지한 사실: 확장자 차단만으로 파일 안전성을 보장하지 못하며, 시험용 내용 진단과 운영 업로드 내용 차단은 구분한다. 앱 코드는 변경하지 않았다.

### 2차: 실제 내용·이름이 다르면?

- AI가 확인한 판단 이유: “확장자만 바꿔 검사를 통과할 수 있는지, 실제 내용을 어디까지 판별할 수 있는지 확인하기 위해서” 시험했는지 물었다.
- 사용자 확정 원문: “네 맞습니다.”
- 반영: 위 목적을 CONSIDERATION.md의 두 번째 판단 행에 명시했다. 샘플·등록 확장자 이름 시험과 UNVERIFIABLE 구분을 유지하고, 진단 결과를 운영 업로드 차단에 적용하지 않았음을 명시했다.
- 변경 경계: 기존 시험 결과의 목적을 설명한 문서 수정이며, 새로운 시험 실행·내용 검사 기능 구현은 아니다.
