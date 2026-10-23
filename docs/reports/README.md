# 파일 업로드 과제 보고서 목록

PR별 구현 결과와 기존 리서치·가이드를 모은 목록입니다. 구현 결과 보고서는 당시 소스 커밋과 실제 CI를 기준으로 작성했습니다. 과거 문서의 미구현·미실행 표시는 당시 기록입니다. 현재 동작은 최신 구현 보고서와 해당 PR의 Checks를 확인하세요.

PR1 HTML은 당시 설명·코드·시험 기록을 유지하면서, 390px 화면에서 가로로 펼쳐지던 grid의 최소 너비만 CSS로 보완했습니다.

## PR별 구현 결과

| 단계 | 핵심 | 클릭형 HTML | GitHub용 MD |
|---|---|---|---|
| [PR #1](https://github.com/feed-mina/flow_fileupload_AI/pull/1) | 정책 선택·저장·재시작 유지·커스텀 추가 | [HTML](2026-10-23/flow-pr1-implementation-report-20261023.html) | [Markdown](2026-10-23/flow-pr1-implementation-report-20261023.md) |
| [PR #2](https://github.com/feed-mina/flow_fileupload_AI/pull/2) | 업로드 검증·UUID 저장·정책 버전·커스텀 삭제 | [HTML](2026-10-23/flow-pr2-implementation-report-20261023.html) | [Markdown](2026-10-23/flow-pr2-implementation-report-20261023.md) |
| [PR #3](https://github.com/feed-mina/flow_fileupload_AI/pull/3) | 정책 감사·응답 유실 조회·UX와 예외 처리 | [HTML](2026-10-23/flow-pr3-implementation-report-20261023.html) | [Markdown](2026-10-23/flow-pr3-implementation-report-20261023.md) |

HTML은 내려받아 브라우저로 열면 클릭형 다이어그램이 동작합니다. GitHub 웹에서는 Markdown의 다이어그램·소스 줄 링크를 읽을 수 있습니다.

## UI 리서치와 예상 화면

기존에 올라간 문서·그림은 원래 경로를 유지합니다. 아래 문서는 구현 전 리서치·예상 GUI이며 실제 구현 캡처와 구분합니다.

| 자료 | 링크 |
|---|---|
| 쉬운 적용 가이드 HTML | [easy.html](../2026-10-23/flow-ui-research-20261023-105551-easy.html) |
| 쉬운 적용 가이드 MD | [easy.md](../2026-10-23/flow-ui-research-20261023-105551-easy.md) |
| 문제 해결 가이드 HTML | [fix.html](../2026-10-23/flow-ui-research-20261023-105551-fix.html) |
| 문제 해결 가이드 MD | [fix.md](../2026-10-23/flow-ui-research-20261023-105551-fix.md) |
| 초기 상태 예상 화면 | [gui-initial.svg](../2026-10-23/flow-ui-research-20261023-105551-gui-initial.svg) |
| 차단 상태 예상 화면 | [gui-blocked.svg](../2026-10-23/flow-ui-research-20261023-105551-gui-blocked.svg) |
| 리서치 보고서 검수 기록 | [qa.md](../2026-10-23/flow-ui-research-20261023-105551-qa.md) |

## 수행 기준과 계약

- [전체 구현 가이드](../IMPLEMENTATION_GUIDE.md)
- [PR1 정책 최소 구현 계약](../POLICY_SLICE.md)
- [PR2 검증·보안 DEC 계약](../DEC_IMPLEMENTATION.md)
- [PR3 UX·예외 계약](../UX_IMPLEMENTATION.md)
- [시험 이력](../TEST_RESULTS.md)
- [AI 활용 기록](../PROMPT_LOG.md)

이번 문서 PR은 PR1·PR2·PR3 결과 보고서 HTML/MD 6개를 보관하고 이 목록을 추가합니다. 개인 대화 원문·다운로드 임시 파일은 저장소 업로드 범위에 포함하지 않습니다.
