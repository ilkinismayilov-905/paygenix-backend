package com.example.paygenix.config;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.dto.response.LegalDocumentResponse;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = JsonMapper.builder().build();

        GenericJacksonJsonRedisSerializer serializer =
                new GenericJacksonJsonRedisSerializer(objectMapper);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    // One concrete type per cache: JSON without embedded class names, so cached bytes cannot pick the type to build.
    // Immediate writes: with Lettuce the default writer evicts asynchronously, so a read right after an update could still see old data.
    @Bean
    public RedisCacheManagerBuilderCustomizer jsonCacheSerializers(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = JsonMapper.builder().build();
        JavaType profileList = mapper.getTypeFactory().constructCollectionType(List.class, EditProfileResponse.class);
        return builder -> builder
                .cacheWriter(RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites()))
                .withCacheConfiguration(CacheNames.EDIT_PROFILE_BY_ID,
                        json(new JacksonJsonRedisSerializer<>(mapper, EditProfileResponse.class)))
                .withCacheConfiguration(CacheNames.EDIT_PROFILE_ALL,
                        json(new JacksonJsonRedisSerializer<List<EditProfileResponse>>(mapper, profileList)))
                .withCacheConfiguration(CacheNames.LEGAL_DOCUMENT_BY_TYPE,
                        json(new JacksonJsonRedisSerializer<>(mapper, LegalDocumentResponse.class)));
    }

    private static RedisCacheConfiguration json(JacksonJsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
