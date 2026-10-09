package com.zhiguang;

import com.zhiguang.auth.token.JwtService;
import com.zhiguang.auth.token.RefreshTokenStore;
import com.zhiguang.auth.token.TokenPair;
import com.zhiguang.user.domain.User;
import com.zhiguang.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

@SpringBootTest
class ZhiguangApplicationTests {

    @Resource
    UserMapper userMapper;

    @Test
    void userMapperTest(){
        User user = userMapper.findByPhone("13619177257");
        System.out.println(user);
    }

    @Resource
    PasswordEncoder passwordEncoder;

    @Test
    void PasswordEncoder(){
        String hash = passwordEncoder.encode("123321");
        System.out.println(hash);
        System.out.println(passwordEncoder.matches("123321",hash));
    }

    @Resource
    JwtService jwtService;

    @Test
    void JwtTest(){
        User user = new User();
        user.setPhone("11111111111");
        user.setNickname("achan");
        user.setId(1L);
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        System.out.println("tokenPair:" + tokenPair);

        Jwt jwt1 = jwtService.decode(tokenPair.accessToken());
        Jwt jwt2 = jwtService.decode(tokenPair.refreshToken());
        System.out.println(jwtService.extractTokenType(jwt1));

    }

    void testPasswordReset(){

    }


}
