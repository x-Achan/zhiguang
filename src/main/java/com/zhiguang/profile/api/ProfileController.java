package com.zhiguang.profile.api;

import com.zhiguang.auth.token.JwtService;
import com.zhiguang.profile.api.dto.ProfilePatchRequest;
import com.zhiguang.profile.api.dto.ProfileResponse;
import com.zhiguang.profile.service.ProfileService;
import com.zhiguang.storage.OssStorageService;
import com.zhiguang.storage.config.OssProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {


    private final JwtService jwtService;
    private final ProfileService profileService;
    private final OssStorageService ossStorageService;


    @PatchMapping("/patch")
    public ProfileResponse patch(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ProfilePatchRequest request){
        long userId = jwtService.extractUserId(jwt);
        return profileService.updateProfile(userId,request);
    }

    // 上窜头像并更新头像地址
    @PostMapping("/avatar")
    public ProfileResponse avatar(@AuthenticationPrincipal Jwt jwt,
                                  @RequestPart("file") MultipartFile file, Principal principal){
        long userId = jwtService.extractUserId(jwt);
        // 1.先调用OSS，生成url
        String url = ossStorageService.uploadAvatar(userId, file);
        // 2.将url保存到 user中，返回响应
        return profileService.updateAvatar(userId,url);
    }

}
