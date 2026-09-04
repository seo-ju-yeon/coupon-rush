-- 기존 사용자는 일반 사용자 권한으로 설정함
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 정의되지 않은 권한이 저장되는 것을 방지함
ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'));

COMMENT ON COLUMN users.role IS '사용자 권한';
