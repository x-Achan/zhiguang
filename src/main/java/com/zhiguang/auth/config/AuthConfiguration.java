package com.zhiguang.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
@Configuration
@RequiredArgsConstructor
public class AuthConfiguration {

    private final AuthProperties properties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(
                properties.getPassword().getBcryptStrength() //传入加密强度
        );
    }
    /**
     * 负责签发（用私钥+公钥构造，私钥用于签名）
     * @return
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        // 读取AuthProperties中的配置对象
        AuthProperties.Jwt jwtProps = properties.getJwt();
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(jwtProps.getPrivateKey());
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());

        RSAKey jwk = new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(jwtProps.getKeyId())
                        .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 负责验证（只用公钥，验签）
     * @return
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        RSAPublicKey publicKey = PemUtils.readPublicKey(properties.getJwt().getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}