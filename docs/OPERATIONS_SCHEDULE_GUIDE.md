# DB 감사·로그 관리 예약 작업 안내서

작성일: 2026-10-23. 대상: DB-O05. **이 문서는 실행 방법 제안이며 cron 설치·삭제 작업은 실행하지 않았다.** 업로드 정상 파일의 보관/총량 정책(DEC-13)과 구분한다.

cron은 서버가 정해진 시간에 명령을 실행하도록 예약하는 도구다. PostgreSQL 앱 계정 `flow_app`에는 감사 삭제 권한이 없으므로 유지보수 작업은 별도 관리 계정으로 수행한다. 비밀번호는 명령줄/로그에 쓰지 않고 권한 600의 `.pgpass` 또는 서버의 비밀 관리 방식을 사용한다.

## 시간과 단계

| 작업 | 한국 시간(Asia/Seoul) | 서버가 UTC이면 | 내용 |
|---|---|---|---|
| 매일 점검 | 매일 02:00 | 매일 17:00(전날 UTC 날짜) | 용량·오래된 감사 행 수·이상 로그 확인, 승인한 기간의 오래된 행만 소량 정리 |
| 주간 점검 | 일요일 03:00 | 토요일 18:00 UTC | 백업/복원 상태 확인, 통계·일반 VACUUM, 오류 추세와 보관 기준 검토 |

서버에서 `timedatectl`로 실제 시간대를 확인한다. Ubuntu cron은 서버 시간대를 기준으로 운용한다. 모든 cron 구현이 `CRON_TZ`를 지원한다고 가정하지 않는다. 다음은 **서버 시간대가 Asia/Seoul일 때의 설치 전 예시**다. 아래 `/usr/local/sbin/flow-maintenance`는 아직 만들거나 설치하지 않은 후속 관리 스크립트 자리다.

```cron
# 실제 설치하지 않은 예시. 스크립트·권한·백업·보관 일수를 먼저 정한다.
0 2 * * * flock -n /var/lock/flow-maintenance.lock /usr/local/sbin/flow-maintenance daily --dry-run
0 3 * * 0 flock -n /var/lock/flow-maintenance.lock /usr/local/sbin/flow-maintenance weekly --dry-run
```

UTC 서버에서는 각각 `0 17 * * *`, `0 18 * * 6`으로 바꾼다. 일요일에도 일일 작업은 02시에, 주간 작업은 03시에 실행한다. 같은 lock 파일로 겹친 실행을 막고, 일일 작업이 끝나지 않았으면 주간 작업은 건너뛰고 알림/기록을 남기는 방식을 제안한다.

## 점진적으로 적용하는 방법

1. 첫 단계는 **조회만** 한다. 감사 행 수, 가장 오래된 시각, 테이블·인덱스 크기, 디스크 잔여량, 오류 코드별 건수를 기록한다.
2. 호스팅과 보관 일수, 백업 저장 위치·복원 가능 여부를 확정한다. 30일·90일은 가능한 예시일 뿐 이번 과제의 확정값이 아니다.
3. 보관 대상과 삭제 후보를 먼저 별도 저장하고 복원 시험을 한다. 감사 삭제는 정책 변경 증거를 없애므로 운영자가 기간과 필요성을 정해야 한다.
4. 승인한 기간보다 오래된 감사만 1회 최대 500행, 일일 최대 20묶음처럼 작은 작업으로 나누는 방식을 검토한다. 잠금 대기나 DB 부하가 늘면 중지하고 다음 날 이어간다. 예시 묶음 수 역시 설치 전에 조정한다.
5. 주간에는 `VACUUM (ANALYZE) policy_change` 등 일반 정리를 검토한다. 서비스 중 전체 테이블 잠금을 유발하는 `VACUUM FULL`을 자동 예약하지 않는다.

점검용 SQL(읽기 전용):

```sql
SELECT count(*) AS rows, min(created_at) AS oldest,
       pg_size_pretty(pg_total_relation_size('policy_change')) AS total_size
FROM policy_change;
```

아래 SQL은 **후속 관리 스크립트를 만들 때 사용할 삭제 제안**이다. 먼저 동일한 조건으로 SELECT 결과를 확인하고 백업·보관 일수를 확정한다. `retention_days`는 관리자가 전달하는 검증된 양의 정수이며 임의 기본값으로 실행하지 않는다.

```sql
-- psql --set=retention_days=운영자가_확정한_일수
-- 실행 전: 동일 WHERE의 count(*)와 삭제 후보 id/시각을 조회해 검토한다.
WITH candidates AS (
  SELECT id FROM policy_change
  WHERE created_at < now() - make_interval(days => :'retention_days'::int)
  ORDER BY id LIMIT 500 FOR UPDATE SKIP LOCKED
)
DELETE FROM policy_change
WHERE id IN (SELECT id FROM candidates)
RETURNING id;
```

일일 기록에는 시작/종료 시각, dry-run 여부, 조회/삭제 건수, 보관 기준, 소요시간과 오류 유형만 남긴다. 비밀번호·세션 쿠키·CSRF 토큰·개인 파일명은 남기지 않는다.

## 로그는 감사 테이블과 별도로 관리한다

서버는 구조화된 JSON 로그를 표준 출력으로 내보낸다. Docker가 보관하는 실행 중 로그 파일을 직접 삭제하거나 0바이트로 덮어쓰지 않는다. 호스팅 선정 후 Docker 로그 드라이버의 용량 제한/순환, 로그 수집 경로, 보관 일수를 먼저 정한다. cron은 완료된 로그 보관본을 압축·정리하고 오류 추세를 점검하는 역할로 나눈다.

로그 보관본의 정리도 삭제 전에 목록만 출력하고, 경로가 전용 로그 보관 디렉터리인지 검증한다. PostgreSQL 데이터 볼륨·정상 업로드 볼륨을 이 작업에 포함하지 않는다. 이 안내서는 DB-O05 자동화가 구현되었다는 증거가 아니다.

공식 참고: [PostgreSQL VACUUM](https://www.postgresql.org/docs/17/sql-vacuum.html), [PostgreSQL 정기 vacuum](https://www.postgresql.org/docs/17/routine-vacuuming.html), [Docker 로그 순환](https://docs.docker.com/engine/logging/drivers/json-file/).
