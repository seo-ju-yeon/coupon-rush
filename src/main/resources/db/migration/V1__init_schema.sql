-- 사용자 테이블
-- 초기에는 인증 없이 userId로 사용자를 식별함
CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 같은 이메일로 중복 가입되는 것을 방지
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 쿠폰 원본 테이블
-- 발급 가능한 쿠폰의 수량, 기간, 상태를 관리
CREATE TABLE coupons
(
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    discount_amount INTEGER      NOT NULL,
    total_quantity  INTEGER      NOT NULL,
    issued_quantity INTEGER      NOT NULL DEFAULT 0,
    starts_at       TIMESTAMP    NOT NULL,
    ends_at         TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- JPA 낙관적 락 실험을 위한 버전 컬럼
    version         BIGINT       NOT NULL DEFAULT 0,

    -- 할인 금액은 0보다 커야 함
    CONSTRAINT chk_coupons_discount_amount CHECK (discount_amount > 0),
    -- 총 발급 수량은 0보다 커야 함
    CONSTRAINT chk_coupons_total_quantity CHECK (total_quantity > 0),
    -- 발급 수량은 음수가 될 수 없음
    CONSTRAINT chk_coupons_issued_quantity CHECK (issued_quantity >= 0),
    -- 발급 수량은 총 수량을 초과할 수 없음
    CONSTRAINT chk_coupons_quantity_limit CHECK (issued_quantity <= total_quantity),
    -- 쿠폰 상태는 정해진 값만 허용함
    CONSTRAINT chk_coupons_status CHECK (status IN ('READY', 'OPEN', 'CLOSED', 'EXPIRED'))
);

-- 이벤트 게시글 테이블
-- 게시글은 쿠폰과 연결될 수도 있고, 연결되지 않을 수도 있음.
CREATE TABLE posts
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    coupon_id  BIGINT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    -- 게시글에 연결된 쿠폰
    CONSTRAINT fk_posts_coupon
        FOREIGN KEY (coupon_id)
            REFERENCES coupons (id)
);

-- 쿠폰 발급 내역 테이블
-- 특정 사용자가 특정 쿠폰을 발급받은 기록을 저장
CREATE TABLE coupon_issues
(
    id            BIGSERIAL PRIMARY KEY,
    coupon_id     BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    issued_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at       TIMESTAMP,

    -- 쿠폰 사용 시 생성된 주문 ID
    -- orders 테이블 생성 후 FK를 추가
    used_order_id BIGINT,

    -- 발급된 쿠폰 원본
    CONSTRAINT fk_coupon_issues_coupon
        FOREIGN KEY (coupon_id)
            REFERENCES coupons (id),

    -- 쿠폰을 발급받은 사용자
    CONSTRAINT fk_coupon_issues_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),

    -- 같은 사용자는 같은 쿠폰을 한 번만 발급받을 수 있음
    -- 동시 요청에서도 중복 발급을 막는 DB 방어선
    CONSTRAINT uk_coupon_issues_coupon_user
        UNIQUE (coupon_id, user_id),

    -- 발급 쿠폰 상태는 정해진 값만 허용
    CONSTRAINT chk_coupon_issues_status
        CHECK (status IN ('ISSUED', 'USED', 'EXPIRED', 'CANCELLED'))
);

-- 주문 테이블
-- 실제 결제는 구현하지 않고, 쿠폰 사용 결과로 주문을 생성함
CREATE TABLE orders
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    original_amount INTEGER     NOT NULL,
    discount_amount INTEGER     NOT NULL,
    final_amount    INTEGER     NOT NULL,
    coupon_issue_id BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 주문 사용자
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),

    -- 주문에 사용된 발급 쿠폰
    CONSTRAINT fk_orders_coupon_issue
        FOREIGN KEY (coupon_issue_id)
            REFERENCES coupon_issues (id),

    -- 하나의 발급 쿠폰은 하나의 주문에만 사용할 수 있음
    -- 같은 couponIssueId로 동시에 주문 요청이 들어와도 중복 사용을 막음
    CONSTRAINT uk_orders_coupon_issue
        UNIQUE (coupon_issue_id),

    -- 주문 금액은 음수가 될 수 없음
    CONSTRAINT chk_orders_original_amount CHECK (original_amount >= 0),
    CONSTRAINT chk_orders_discount_amount CHECK (discount_amount >= 0),
    CONSTRAINT chk_orders_final_amount CHECK (final_amount >= 0),

    -- 주문 상태는 정해진 값만 허용
    CONSTRAINT chk_orders_status CHECK (status IN ('CREATED', 'CANCELLED'))
);

-- coupon_issues.used_order_id는 orders.id를 참조
-- coupon_issue와 order가 서로 참조하므로 orders 생성 후 FK를 추가
ALTER TABLE coupon_issues
    ADD CONSTRAINT fk_coupon_issues_used_order
        FOREIGN KEY (used_order_id)
            REFERENCES orders (id);

-- 조회 성능을 위한 인덱스
CREATE INDEX idx_posts_coupon_id ON posts (coupon_id);
CREATE INDEX idx_coupon_issues_user_id ON coupon_issues (user_id);
CREATE INDEX idx_coupon_issues_coupon_id ON coupon_issues (coupon_id);
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- 테이블 및 컬럼 설명
COMMENT ON TABLE users IS '사용자 테이블';
COMMENT ON COLUMN users.id IS '사용자 ID';
COMMENT ON COLUMN users.email IS '사용자 이메일';
COMMENT ON COLUMN users.nickname IS '사용자 닉네임';
COMMENT ON COLUMN users.created_at IS '사용자 생성 일시';

COMMENT ON TABLE coupons IS '쿠폰 원본 테이블';
COMMENT ON COLUMN coupons.id IS '쿠폰 ID';
COMMENT ON COLUMN coupons.name IS '쿠폰 이름';
COMMENT ON COLUMN coupons.discount_amount IS '할인 금액';
COMMENT ON COLUMN coupons.total_quantity IS '총 발급 가능 수량';
COMMENT ON COLUMN coupons.issued_quantity IS '현재 발급된 수량';
COMMENT ON COLUMN coupons.starts_at IS '발급 시작 일시';
COMMENT ON COLUMN coupons.ends_at IS '발급 종료 일시';
COMMENT ON COLUMN coupons.status IS '쿠폰 상태';
COMMENT ON COLUMN coupons.created_at IS '쿠폰 생성 일시';
COMMENT ON COLUMN coupons.updated_at IS '쿠폰 수정 일시';
COMMENT ON COLUMN coupons.version IS '낙관적 락 버전';

COMMENT ON TABLE posts IS '이벤트 게시글 테이블';
COMMENT ON COLUMN posts.id IS '게시글 ID';
COMMENT ON COLUMN posts.title IS '게시글 제목';
COMMENT ON COLUMN posts.content IS '게시글 본문';
COMMENT ON COLUMN posts.coupon_id IS '게시글에 연결된 쿠폰 ID';
COMMENT ON COLUMN posts.created_at IS '게시글 생성 일시';
COMMENT ON COLUMN posts.updated_at IS '게시글 수정 일시';
COMMENT ON COLUMN posts.deleted_at IS '게시글 삭제 일시';

COMMENT ON TABLE coupon_issues IS '쿠폰 발급 내역 테이블';
COMMENT ON COLUMN coupon_issues.id IS '쿠폰 발급 내역 ID';
COMMENT ON COLUMN coupon_issues.coupon_id IS '발급된 쿠폰 원본 ID';
COMMENT ON COLUMN coupon_issues.user_id IS '쿠폰을 발급받은 사용자 ID';
COMMENT ON COLUMN coupon_issues.status IS '발급 쿠폰 상태';
COMMENT ON COLUMN coupon_issues.issued_at IS '쿠폰 발급 일시';
COMMENT ON COLUMN coupon_issues.used_at IS '쿠폰 사용 일시';
COMMENT ON COLUMN coupon_issues.used_order_id IS '쿠폰 사용 시 생성된 주문 ID';

COMMENT ON TABLE orders IS '주문 테이블';
COMMENT ON COLUMN orders.id IS '주문 ID';
COMMENT ON COLUMN orders.user_id IS '주문 사용자 ID';
COMMENT ON COLUMN orders.original_amount IS '할인 전 주문 금액';
COMMENT ON COLUMN orders.discount_amount IS '쿠폰 할인 금액';
COMMENT ON COLUMN orders.final_amount IS '최종 주문 금액';
COMMENT ON COLUMN orders.coupon_issue_id IS '주문에 사용된 발급 쿠폰 ID';
COMMENT ON COLUMN orders.status IS '주문 상태';
COMMENT ON COLUMN orders.created_at IS '주문 생성 일시';
