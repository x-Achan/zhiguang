package com.zhiguang.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private final Jwt jwt = new Jwt();
    private final Verification verification = new Verification();
    private final Password password = new Password();
    /**
     * Jwt令牌相关
     */
    @Data
    public static class Jwt {
        private String issuer = "zhiguang"; //token签发者标识
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private String keyId = "zhiguang-key"; //keyId用于密钥轮换（暂时不需要轮换）
        private Resource privateKey; //私钥
        private Resource publicKey; //公钥
    }
    /**
     * 验证码相关
     */
    @Data
    public static class Verification {
        private int codeLength = 6; //验证码位数
        private Duration ttl = Duration.ofMinutes(5); //有效期5min
        private int maxAttempts = 5; //最大尝试次数
        private Duration sendInterval = Duration.ofSeconds(60); //发送间隔 60s
        private int dailyLimit = 10; //每天上限
    }
    /**
     * 密码策略相关
     */
    @Data
    public static class Password {
        private int bcryptStrength = 12; //BCrypt 加密强度
        private int minLength = 8; //密码最小长度
    }
}