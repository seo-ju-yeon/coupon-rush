package dev.portfolio.couponrush.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port
    ) {
        // 단일 Redis 서버에 연결하기 위한 Redisson 설정을 생성함
        Config config = new Config();

        /*
        Redis를 사용하는 첫 요청이 들어올 때 실제 연결함
        Redis를 사용하지 않는 기존 테스트가 Redis 실행 여부에 의존하지 않도록 설정함
         */
        config.setLazyInitialization(true);

        // redis:// 프로토콜을 사용하여 로컬 Redis 접속 주소를 설정함
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);

        return Redisson.create(config);
    }
}
