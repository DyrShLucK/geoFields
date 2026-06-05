package com.geofields.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Хранит HTTP-сессию (в т.ч. Spring Security) в Redis — переживает перезапуск JVM.
 * Отключено в тестах через {@code spring.session.store-type=none}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisHttpSession(redisNamespace = "geofields:session")
public class RedisHttpSessionConfiguration {
}
