# 과제 제출 점검표

작성일: 2026-10-23 한국 시간

**공유 확장자 차단 정책을 PostgreSQL에 저장하고, Spring Boot가 실제 업로드에 적용하며 React 화면에서 결과를 확인한다.**

| 제출 항목 | 위치 | 상태 |
|---|---|---|
| 저장소 | [feed-mina/flow_fileupload_AI](https://github.com/feed-mina/flow_fileupload_AI) | 준비 |
| 공개 화면 | [Vercel 화면](https://flow-fileupload-ai.vercel.app/) | 공개 Chromium 시험 통과 |
| 서버·API | [Hostinger](https://flow.srv1869569.hstgr.cloud/) · [정책 JSON](https://flow-fileupload-ai.vercel.app/api/policy) | 조회·쓰기·업로드 경로 시험 통과 |
| 실행 방법 | [README](../README.md) | 로컬·실제 배포 구분 |
| 고려사항·구현 상태 | [주석 가이드](IMPLEMENTATION_GUIDE_ANNOTATED.md) | PR #6의 공개 검증 결과 추가 |
| 실제 배포 이력 | [배포 기록](DEPLOYMENT_PROGRESS_2026-10-23.md) | 최초 기록과 후속 검증 구분 |
| 공개 시험 결과 | [6개 항목 보고서](POST_DEPLOYMENT_CHECKS.md) · [증거 JSON](evidence/public-deployment-2026-10-23.json) | 11개 통과 |
| 기존 회귀 시험 | [시험 기록](TEST_RESULTS.md) · [PR #6 Actions](https://github.com/feed-mina/flow_fileupload_AI/pull/6/checks) | 최종 커밋의 실행 결과 확인 |
| 구현 설명 | [PR1~3 보고서 목록](reports/README.md) · [DEC 구현](DEC_IMPLEMENTATION.md) · [UX 구현](UX_IMPLEMENTATION.md) | 준비 |
| AI 활용 기록 | [PROMPT_LOG](PROMPT_LOG.md) | 요청·채택·검증 기록 |
| 지원자 최종 회고 | 아래 작성 항목 | **지원자 본인 작성 필요** |

## 평가자가 확인할 흐름

1. 공개 화면에서 고정 확장자 7개와 커스텀 개수를 확인한다. 모든 방문자가 같은 정책을 공유하므로 최초 배포 시의 미선택 상태를 현재 상태로 단정하지 않는다.
2. 고정 항목 하나의 기존 값을 기록하고 변경 → 새로고침 → 기존 값 복원을 확인한다.
3. 다른 항목과 겹치지 않는 커스텀을 추가하고 삭제한다.
4. 무해한 작은 파일로 허용·차단 결과를 확인한다. 실제 내용 검사·악성코드 검사 서비스는 아니다.
5. 결과가 불확실하면 요청 번호로 상태를 조회한다. 무조건 다시 올리지 않는다.

세션·CSRF 방어는 존재하지만 데모 로그인은 운영 환경에서 설정하지 않아 `loginAvailable=false`였다. 로그인 없이 공유 정책을 편집할 수 있으며, 사용자별 정책·편집 권한 보호 완료로 설명하지 않는다. DB 관리자 계정·비밀번호·.env·업로드 원문·백업 파일은 제출하지 않는다.

## 지원자가 직접 마무리할 회고

아래 질문은 지원자 본인의 경험으로 작성한다. AI가 지원자의 느낀 점이나 책임 판단을 대신 사실처럼 작성하지 않는다.

- AI에게 맡긴 작업과 직접 결정한 작업은 무엇인가?
- 생성 결과에서 발견한 오류와 수정 근거는 무엇인가?
- 실제 시험한 환경과 아직 시험하지 않은 범위는 무엇인가?
- 이번 과제에서 구현하지 않기로 결정한 사항과 이유는 무엇인가?
- 시간이 더 있다면 가장 먼저 개선할 한 가지는 무엇인가?

## 이번 범위에서 하지 않은 작업

사용자 지정으로 컨테이너 재생성 영속성·백업·복원 시험은 제외했다. 실제 서버의 이전 이미지 복귀도 실행하지 않았다. 정상 파일 보관·총량, 정책 편집 인가·사용자 격리, 운영 cron 설치, 신규 readiness/자동복구, FE-U07 확장은 기존 보류 결정을 유지한다.
