package dev.portfolio.couponrush.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
// JWT 생성과 검증에 필요한 객체(암호키, 인코더, 디코더)를 Spring Bean으로 등록함
public class JwtConfig {

    private static final int MINIMUM_SECRET_KEY_LENGTH = 32;

    @Bean
    // 환경변수의 Base64 문자열을 HS256에서 사용할 SecretKey로 변환함
    public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
        byte[] keyBytes;

        /*
        JWT_SECRET에는 바이너리 키를 직접 저장하지 않고 문자열로 안전하게 표현한 Base64 값이 저장되어 있음
        예: oepnssl rand -base64 32 -> 32바이트 난수를 생성한 후 Base64 문자열로 출력함

        JWT 서명에는 실제 바이트가 필요하므로 Base64 문자열을 원래의 바이트 배열로 복원
         */
        try {
            keyBytes = Base64.getDecoder()
                    .decode(jwtProperties.secret().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT_SECRET은 Base64 형식이어야 합니다.", e);
        }

        /*
        HS256은 최소 256비트 수준의 비밀 키를 사용하는 것이 적절함
        1바이트는 8비트이므로 32바이트는 256비트에 해당함
         */
        if (keyBytes.length < MINIMUM_SECRET_KEY_LENGTH) {
            throw new IllegalArgumentException("JWT_SECRET은 32바이트 이상이어야 합니다.");
        }

        /*
        복원한 바이트 배열을 Java 암호화 API가 사용할 수 있는 SecretKey로 변환함
        - HmacSHA256: 동일한 비밀 키를 이용하여 JWT 서명을 생성하고 검증하는 알고리즘
         */
        return new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );
    }

    @Bean
    // 로그인 성공 시 JWT를 생성하고 서명할 JwtEncoder를 등록함
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        /*
        - OctetSequenceKey: 문자열이나 바이트 형태의 대칭 키를 JWK 표준 형식으로 표현
        - HS256 : JWT 생성과 검증에 동일한 비밀 키를 사용하는 대칭키 방식
         */
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey)
                .algorithm(JWSAlgorithm.HS256)
                .build();

        /*
        NimbusJwtEncoder는 서명에 사용할 키를 JWKSource에서 조회함
        현재는 키가 하나지만 Nimbus가 요구하는 표준 구조에 맞추기 위해 키 하나를 JWKSet으로 감싼 후 변경 불가능한 키 저장소를 만듦
        여기서 SecurityContext는 Spring SecurityContext가 아니라, Nimbus가 키를 선택할 때 사용할 수 있는 문맥 타입임
         */
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));

        /*
        Nimbus JOSE + JWT 라이브러리를 사용하는 JwtEncoder 구현체를 반환함
        이후 JwtTokenProvider가 사용자 Claim을 전달하면 실제 JWT를 생성함
         */
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    // API 요청으로 전달된 JWT를 검증할 JwtDecoder를 등록함
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties jwtProperties
    ) {
        /*
        JwtEncoder가 서명에 사용한 것과 동일한 비밀 키를 사용함
        JwtDecoder의 decode는 복호화를 의미하지 않으며, JWT의 Header와 Payload를 해석하고 서명과 Claim을 검증함
         */
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        /*
        Spring Security의 기본 JWT 검증과 issuer 검증을 함께 설정함
        기본 검증:
        - exp: 토큰 만료시간을 검증함
        - nbf: 토큰 사용 가능 시작 시간을 검증함
        issuer 검증:
        - 토큰의 iss가 coupon-rush인지 검증함
         */
        jwtDecoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        jwtProperties.issuer()
                )
        );

        /*
        검증이 완료된 JwtDecoder를 Spring Bean으로 반환함
        이후 OAuth2 Resource Server가 API 요청의 Bearer Token 검증에 사용함
         */
        return jwtDecoder;
    }
}
