## 2026-11-25 (레벨 0 통과)
- 발급 성공: Redis 명령 4개, SQL 3개
- 소진: Redis 2개, SQL 1개 / 중복: Redis 1개, SQL 1개
- 같은 409라도 code로 구분 (ALREADY_ISSUED vs QUANTITY_EXHAUSTED)
- MONITOR 읽는 법: 127.0.0.1 ping = 헬스체크, 192.168.65.1 = 내 앱, lua = 스크립트 안
- http 변수는 저장하는 요청을 실행해야 생기고, 실패 응답엔 id가 없다
- 204는 성공이지만 본문이 없다
