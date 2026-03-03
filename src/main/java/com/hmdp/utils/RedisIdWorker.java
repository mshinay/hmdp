package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = LocalDateTime.of(2020, 1, 1, 0, 0, 0)
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli();
    private static final int SEQ_BITS = 22;
    private static final long MAX_SEQUENCE = (1L << SEQ_BITS) - 1;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        long now = System.currentTimeMillis();
        long timestamp = now - BEGIN_TIMESTAMP;

        String date = LocalDateTime.now().format(DATE_FORMATTER);
        Long seq = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        if (seq == null) {
            throw new IllegalStateException("Redis increment failed");
        }
        if (seq > MAX_SEQUENCE) {
            throw new IllegalStateException("Sequence overflow for keyPrefix=" + keyPrefix + ", seq=" + seq);
        }

        return (timestamp << SEQ_BITS) | seq;
    }
}
