-- 사용자 비밀번호의 암호화 결과를 저장할 컬럼을 추가함
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100);

-- 기존 사용자는 로그인할 수 없는 임시 값으로 설정함
-- UNAVAILABLE : 실제 비밀번호가 아니라 기존 계정을 로그인 불가능하게 만드는 임시 값
UPDATE users
SET password_hash = 'UNAVAILABLE'
WHERE password_hash IS NULL;

-- 모든 사용자가 암호화된 비밀번호를 가지도록 필수 제약조건을 설정함
ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

COMMENT ON COLUMN users.password_hash IS '암호화된 사용자 비밀번호';
