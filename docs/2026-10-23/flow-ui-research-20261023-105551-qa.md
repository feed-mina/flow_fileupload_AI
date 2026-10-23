# 보고서 검수 기록

기준일: 2026-10-23

- 고유 점검 항목: 108개. 이전 123개 집계는 재등장 번호가 포함된 오류였음.
- 원래 앱 시험 시나리오: 28개, 전부 미실행.
- 레퍼런스: 공식 저장소 10개와 관련 소스·자료 확인.

## 실행한 문서 검사

- flow-ui-research-20261023-105551-easy.md: tables / image links / Mermaid fences / item mapping / reader contract
- flow-ui-research-20261023-105551-easy.html: UTF-8 / unique IDs / anchor targets / 5 map panels / no external resources / initial 7 unchecked / after 2 selected
- flow-ui-research-20261023-105551-fix.md: tables / image links / Mermaid fences / item mapping / reader contract
- flow-ui-research-20261023-105551-fix.html: UTF-8 / unique IDs / anchor targets / 5 map panels / no external resources / initial 7 unchecked / after 2 selected
- flow-ui-research-20261023-105551-gui-initial.svg: valid SVG / expected GUI label
- flow-ui-research-20261023-105551-gui-blocked.svg: valid SVG / expected GUI label

## 실행하지 못한 검사

- 로컬 보고서의 실제 브라우저 화면·클릭·키보드·좁은 화면 검수: 검수 브라우저에서 127.0.0.1 연결 시간 초과로 미확인.
- 앱 서버 검증·DB 동시성·파일 영속성·공개 배포 시험: 앱 미구현으로 미실행.
- 의존성 호환·번들 크기·성능·사용성 개선 효과: 미측정.

HTML은 외부 의존성 없이 동작하는 CSS 선택형 관계도이며, GUI는 실제 서비스가 아닌 예상 모형이다. 정적 검사 통과를 실제 브라우저나 앱 시험 통과로 해석하지 않는다.
