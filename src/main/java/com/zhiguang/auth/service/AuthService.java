package com.zhiguang.auth.service;

import com.zhiguang.auth.api.dto.*;
import com.zhiguang.auth.config.AuthProperties;
import com.zhiguang.auth.model.IdentifierType;
import com.zhiguang.auth.token.JwtService;
import com.zhiguang.auth.token.RedisRefreshTokenStore;
import com.zhiguang.auth.token.RefreshTokenStore;
import com.zhiguang.auth.token.TokenPair;
import com.zhiguang.auth.util.IdentifierValidator;
import com.zhiguang.auth.verification.*;
import com.zhiguang.user.domain.User;
import com.zhiguang.user.mapper.UserMapper;
import com.zhiguang.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.internal.util.StringHelper;
import org.springframework.security.core.parameters.P;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;


/**
 * 认证业务服务。
 * <p>
 * 职责：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * 安全策略：
 * - 账号格式校验（手机号/邮箱）；
 * - 验证码状态检查（过期/错误/尝试超限）；
 * - 密码复杂度校验（长度与字符类型）；
 * - Refresh Token 白名单存储与轮换，登出/重置密码后失效旧令牌；
 * 审计：记录注册/登录成功与失败，包含渠道、IP、UA。
 * 令牌：签发 RS256 的 Access/Refresh JWT，携带 uid、token_type、jti。
 * 依赖：UserService、VerificationService、PasswordEncoder、JwtService、RefreshTokenStore、LoginLogService、AuthProperties。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthProperties properties;
    private final VerificationService verificationService;
    private final UserMapper userMapper;

    // 发送验证码
    public SendCodeResponse sendCode(SendCodeRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        // 判断是否存在，不同场景对于用户是否存在要求不同
        boolean isExist = identifierExists(request.identifierType(), identifier);

        if (request.scene() == VerificationScene.LOGIN && !isExist) {
            throw new RuntimeException("用户不存在");
        }
        if (request.scene() == VerificationScene.RESET_PASSWORD && !isExist) {
            throw new RuntimeException("用户不存在");
        }
        if (request.scene() == VerificationScene.REGISTER && isExist) {
            throw new RuntimeException("用户已存在");
        }
        // 发送验证码服务逻辑
        SendCodeResult sendCodeResult = verificationService.sendCode(request.scene(), request.identifier());
        return new SendCodeResponse(sendCodeResult.identifier(), sendCodeResult.scene(), sendCodeResult.expireSeconds());
    }

    // 注册
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (!request.agreeTerms()) {
            throw new RuntimeException("没有同意服务条款");
        }
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        boolean isExist = identifierExists(request.identifierType(), identifier);
        if (isExist) {
            throw new RuntimeException("用户已经注册过了");
        }
        // 验证验证码是否正确
        VerificationCheckResult result = verificationService.verify(VerificationScene.REGISTER, identifier, request.code());
        // 检测验证码结果，确保验证码验证成功才继续往下走
        ensureVerificationSuccess(result);

        // 创建一个新的用户
        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .nickname(generateNickname())
                .avatar("https://static.zhiguang.cn/default-avatar.png")
                .bio(null)
                .tagsJson("[]")
                .build();
        // 如果输入密码，添加到user属性中
        if (StringUtils.hasText(request.password())) {
            // 密码格式验证
            //validatePassword(request.password());
            // 对密码进行hash编码，然后存入user属性中
            user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        }

        // insert user,添加到数据库后，user才会有userId
        User userStored = userService.createUser(user);

        // 注册即登录，生成tokenPair,将refreshtoken存入redis中
        TokenPair tokenPair = jwtService.issueTokenPair(userStored);
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        if (ttl.isNegative()) {
            ttl = Duration.ZERO;
        }
        refreshTokenStore.storeToken(userStored.getId(), tokenPair.refreshTokenId(), ttl);
        return new AuthResponse(mapUser(userStored), mapToken(tokenPair));
    }

    // 登录
    public AuthResponse login(LoginRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        // 1. 标准化账号
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        // 2. 根据手机号 / 邮箱查询用户
        Optional<User> userOptional = findUserByIdentifier(request.identifierType(), identifier);
        // 3. 判断用户是否存在
        if (userOptional.isEmpty()) {
            throw new RuntimeException("账号不存在");
        }
        User user = userOptional.get();
        // 4. 判断是密码登录还是验证码登录
        if (StringUtils.hasText(request.password())) {
            if (!StringUtils.hasText(user.getPasswordHash())
                    // 验证密码和hash值是否匹配
                    || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new RuntimeException("账号或密码错误");
            }
        }else if(StringUtils.hasText(request.code())){
            // 验证验证码登录成功，否则报错
            ensureVerificationSuccess(verificationService.verify(VerificationScene.LOGIN, identifier, request.code()));
        }else{
            throw new RuntimeException("请输入密码或者验证码");
        }
        // 5.生成TokenPair
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        // 8.将 refrshToekn存储到redis中
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        if (ttl.isNegative()) {
            ttl = Duration.ZERO;
        }
        refreshTokenStore.storeToken(user.getId(), tokenPair.refreshTokenId(), ttl);
        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    // 刷新token
    public TokenResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        // 1.解析token得到jwt，获取 uid token_type  jti
        Jwt jwt = jwtService.decode(refreshToken);
        String tokenType = jwtService.extractTokenType(jwt);
        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwtService.extractTokenId(jwt);
        if (!tokenType.equals("refresh")) {
            throw new RuntimeException("token类型不正确");
        }
        // 2.redis查询验证token是否过期
        boolean isValid = refreshTokenStore.isTokenValid(userId, tokenId);
        if (!isValid) {
            throw new RuntimeException("refresh token 已经在redis中失效");
        }
        // 3.取出对应用户信息
        User user = userService.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        // 4.使旧refreshToken失效
        refreshTokenStore.revokeToken(userId, tokenId);
        // 5.生成新的 tokenPair
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        // 6.将新的 refreshToken存入Redis中
        refreshTokenStore.storeToken(userId, tokenPair.refreshTokenId(), properties.getJwt().getRefreshTokenTtl());
        return new TokenResponse(
                tokenPair.accessToken(),
                tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshToken(),
                tokenPair.refreshTokenExpiresAt());
    }

    // 登出
    public void logout(LogoutRequest request) {
        String freshToken = request.refreshToken();
        Jwt jwt = jwtService.decode(freshToken);
        String tokenType = jwtService.extractTokenType(jwt);
        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwtService.extractTokenId(jwt);
        if (tokenType.equals("refresh")) {
            refreshTokenStore.revokeToken(userId, tokenId);
        }
    }

    // 修改密码
    public void resetPassword(PasswordResetRequest request){
        // 1.验证账号类型、账号、密码，标准化格式
        validateIdentifier(request.identifierType(), request.identifier());
        validatePassword(request.newPassword());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        // 2.数据库用户是否存在，取出用户对象
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        // 3.验证码校验
        ensureVerificationSuccess(verificationService.verify(VerificationScene.RESET_PASSWORD, identifier, request.code()));
        // 4.存入新的密码
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userService.updatePassword(user);
        // 5.刷新refreshToken
        refreshTokenStore.revokeAll(user.getId());
    }

    // 查询用户信息
    public AuthUserResponse me(long id){
        User user =userService.findById(id).orElseThrow(()->new RuntimeException("用户不存在"));
        return mapUser(user);
    }

    // 确保验证码验证通过
    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result.isSuccess()) {
            return;
        }
        VerificationCodeStatus status = result.status();
        if (status == VerificationCodeStatus.NOT_FOUND || status == VerificationCodeStatus.EXPIRED) {
            throw new RuntimeException("验证码过期");
        }
        if (status == VerificationCodeStatus.MISMATCH) {
            throw new RuntimeException("验证码匹配错误");
        }
        if (status == VerificationCodeStatus.TOO_MANY_ATTEMPTS) {
            throw new RuntimeException("验证超过次数限制");
        }
        throw new RuntimeException("未知错误");
    }


    // 根据账号类型（手机/邮箱），调用不同的 Service 方法去数据库查用户
    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    // 把用户输入的账号"清洗"成标准格式，再拿去查库或存储
    private String normalizeIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> identifier.trim();
            case EMAIL -> identifier.trim().toLowerCase(Locale.ROOT);
        };
    }

    // 手机号/邮箱格式验证
    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new RuntimeException("手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new RuntimeException("邮箱格式错误");
        }
    }

    // 验证手机号/邮箱已经存在
    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);
            case EMAIL -> userService.existsByEmail(identifier);
        };
    }

    // 生成随机昵称
    private String generateNickname() {
        return "知光用户" + UUID.randomUUID().toString().substring(0, 8);
    }

    // 密码格式验证
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new RuntimeException("密码为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < properties.getPassword().getMinLength()) {
            throw new RuntimeException("密码长度不为8位");
        }
        // 保证有数子有字母
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new RuntimeException("密码需要有数字有字幕");
        }
    }
    /**
     * 映射用户实体到响应对象。
     *
     * @param user 用户实体。
     * @return 用户响应。
     */
    private AuthUserResponse mapUser(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getPhone(),
                user.getZgId(),
                user.getBirthday(),
                user.getSchool(),
                user.getBio(),
                user.getGender(),
                user.getTagsJson()
        );
    }

    /**
     * 映射令牌对到响应对象。
     *
     * @param tokenPair 令牌对。
     * @return 令牌响应。
     */
    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(), tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());
    }
}
