package com.zhiguang.profile.service.impl;

import com.zhiguang.profile.api.dto.ProfilePatchRequest;
import com.zhiguang.profile.api.dto.ProfileResponse;
import com.zhiguang.profile.service.ProfileService;
import com.zhiguang.user.domain.User;
import com.zhiguang.user.mapper.UserMapper;
import com.zhiguang.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final UserService userService;
    @Override
    public Optional<User> getById(long userId) {
        return Optional.empty();
    }

    @Override
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest req) {
        User currentUser = userService.findById(userId).orElseThrow(()->new RuntimeException("用户不存在"));

        // 因为是 patch可选字段提交，所以没有要求所有字段必须非空，需要判断是否所有字段为空
        // 至少要提交一个字段，否则属于无效请求
        boolean hasAnyField = req.nickname() != null || req.bio() != null || req.gender() != null
                || req.birthday() != null || req.zgId() != null || req.school() != null
                || req.tagJson() != null;
        if (!hasAnyField) {
            throw new RuntimeException("未提交任何更新字段");
        }
        // 知光号唯一性校验
        if (req.zgId() != null && !req.zgId().isBlank()) {
            // 判断个人资料是否存在
            boolean exists = userMapper.existsByZgIdExceptId(req.zgId(), currentUser.getId());
            if (exists) {
                throw new RuntimeException("该知光Id已经被使用");
            }
        }

        // 更新
        User patch = getUser(req, currentUser);
        userMapper.updateProfile(patch);
        // 更新后回读，保证返回数据为最新快照
        User updated = userMapper.findById(userId);
        return toResponse(updated);
    }

    @Override
    public ProfileResponse updateAvatar(long userId, String avatarUrl) {
        User currentUser = userService.findById(userId).orElseThrow(()-> new RuntimeException("用户已经存在"));

        // 仅更新头像字段
        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatarUrl);
        userMapper.updateProfile(patch);

        // 更新后回读，保证返回最新头像地址
        User updated = userMapper.findById(userId);
        return toResponse(updated);
    }

    // 保存非空字段到user中
    private static User getUser(ProfilePatchRequest req, User current) {
        User patch = new User();
        patch.setId(current.getId());
        if (req.nickname() != null) {
            patch.setNickname(req.nickname().trim());
        }
        if (req.bio() != null) {
            patch.setBio(req.bio().trim());
        }
        if (req.gender() != null) {
            patch.setGender(req.gender().trim().toUpperCase());
        }
        if (req.birthday() != null) {
            patch.setBirthday(req.birthday());
        }
        if (req.zgId() != null) {
            patch.setZgId(req.zgId().trim());
        }
        if (req.school() != null) {
            patch.setSchool(req.school().trim());
        }
        if (req.tagJson() != null) {
            patch.setTagsJson(req.tagJson());
        }
        return patch;
    }

    // 将用户对象映射为外部响应的DTO：ProfileResponse
    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getBio(),
                user.getZgId(),
                user.getGender(),
                user.getBirthday(),
                user.getSchool(),
                user.getPhone(),
                user.getEmail(),
                user.getTagsJson()
        );
    }
}
