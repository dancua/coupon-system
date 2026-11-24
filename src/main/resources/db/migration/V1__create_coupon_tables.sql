-- 쿠폰 정책
CREATE TABLE coupon (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    discount_type   ENUM ('FIXED', 'RATE') NOT NULL,
    discount_value  INT          NOT NULL,
    total_quantity  INT          NOT NULL,
    issued_quantity INT          NOT NULL,
    issue_start_at  DATETIME(6)  NOT NULL,
    issue_end_at    DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 사용자에게 발급된 쿠폰. (coupon_id, user_id) unique 제약이 1인 1매의 최종 방어선이다.
CREATE TABLE issued_coupon (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     ENUM ('ISSUED', 'USED', 'EXPIRED') NOT NULL,
    issued_at  DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issued_coupon_coupon_user (coupon_id, user_id),
    KEY idx_issued_coupon_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
