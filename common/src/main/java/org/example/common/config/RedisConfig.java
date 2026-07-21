package org.example.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：提供一个 key 用 String、value 用 JSON 序列化的 RedisTemplate。
 * <p>
 * 位于 common 模块供各业务服务复用。用 {@link ConditionalOnClass} 守卫：
 * 只有当类路径存在 RedisConnectionFactory(即该服务引入了 spring-boot-starter-data-redis)时才生效，
 * 因此依赖 common 但不需要 Redis 的服务(如 gateway)不会因缺类而启动失败。
 * <p>
 * 默认的 RedisTemplate 用 JDK 序列化，写进去的 key/value 在 redis-cli 里是乱码，不便排查，故自定义。
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig {

    /**
     * 通用 RedisTemplate：
     * - key / hashKey 用 String 序列化
     * - value / hashValue 用 GenericJackson2Json 序列化(带类型信息，可反序列化回原对象)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = jackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 纯字符串场景(如存 token、验证码)直接用它，比 RedisTemplate<String,Object> 更省心。
     * Spring Boot 已自动装配 StringRedisTemplate，这里显式声明只为可读性，可按需删除。
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    private GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持任意字段可见，避免 getter/setter 缺失导致序列化丢字段
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 支持 Java 8 时间类型(LocalDateTime 等)
        mapper.registerModule(new JavaTimeModule());
        // 保留类型信息，反序列化时能还原为原始类型
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
