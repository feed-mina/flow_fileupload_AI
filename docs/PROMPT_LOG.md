# 구현 요청과 진행 기록

## 2026-10-23

사용자 요청: 가이드와 리서치에 따라 **T01~T04: 최초 상태 → 선택 저장 → 재시작 유지 → 커스텀 추가**를 구현하고 최소 시험 시나리오를 구현한 후 GitHub PR을 생성한다.

확정 스택: Spring Boot, React, PostgreSQL, Docker, Git. 저장소: `feed-mina/flow_fileupload_AI`.

진행:

1. 기존 가이드·UI 리서치와 문서만 있던 main 상태를 확인했다.
2. `feat/policy-t01-t04` 브랜치에서 정책 DB·Flyway·API와 Ant Design React 화면을 추가했다.
3. 저장 응답 후 상태 확정, 실패 시 입력 유지, 불확실한 저장 결과의 재조회, 버전 충돌 방지를 구현했다.
4. 로컬 Node API 시험 4개가 통과했다. Docker 엔진·npm 네트워크 제한을 확인하여 실제 DB·브라우저 검증은 GitHub Actions에서 실행하도록 구성했다.
5. 재시작 시험에는 exe=false뿐 아니라 scr=true와 동일 버전·행 ID 보존 검증을 포함했다.

입력 정규화·중복·동시성 등 세부 정책은 임시안으로 기록했다. 최소 시험 통과 자체를 모든 DEC의 확정으로 취급하지 않는다. 전체 대화 로그는 별도의 기존 대화 로그 파일에 계속 기록한다.
