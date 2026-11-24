-- 선착순 쿠폰 선점. Redis는 스크립트를 한 번에 하나씩 실행하므로 아래 확인과 추가 사이에 다른 요청이 끼어들 수 없다.
-- KEYS[1] : 쿠폰을 받은 사용자 Set (coupon:{couponId}:issued-users)
-- ARGV[1] : userId
-- ARGV[2] : 총 발급 수량
-- ARGV[3] : 키 만료(초)
-- return  : 1 = 선점 성공, 2 = 이미 받은 사용자, 3 = 수량 소진
if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
    return 2
end
if redis.call('SCARD', KEYS[1]) >= tonumber(ARGV[2]) then
    return 3
end
redis.call('SADD', KEYS[1], ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[3])
return 1
