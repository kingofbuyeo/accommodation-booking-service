package com.yongchul.booking.common.infrastructure.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisConfig {

    /**
     * Redis Keyspace Notification 수신 컨테이너.
     * TTL 만료 이벤트를 감지하기 위해 필요.
     *
     * Redis 서버에 notify-keyspace-events 설정 필요:
     *   redis-cli CONFIG SET notify-keyspace-events KEx
     * docker-compose에서는 command 옵션으로 설정됨.
     */
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
    ): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
        }
}
