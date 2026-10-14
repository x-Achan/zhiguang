package com.zhiguang.relation.api;

import com.zhiguang.auth.token.JwtService;
import com.zhiguang.relation.api.dto.RelationCountsResponse;
import com.zhiguang.relation.api.dto.RelationStatusResponse;
import com.zhiguang.relation.api.dto.RelationUserResponse;
import com.zhiguang.relation.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relation")
@RequiredArgsConstructor
public class RelationController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RelationService relationService;
    private final JwtService jwtService;

    @PostMapping("/follow")
    public boolean follow(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.follow(jwtService.extractUserId(jwt), toUserId);
    }

    @PostMapping("/unfollow")
    public boolean unfollow(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.unfollow(jwtService.extractUserId(jwt), toUserId);
    }

    @GetMapping("/status")
    public RelationStatusResponse status(@RequestParam long toUserId, @AuthenticationPrincipal Jwt jwt) {
        return relationService.getStatus(jwtService.extractUserId(jwt), toUserId);
    }

    @GetMapping("/following")
    public List<RelationUserResponse> following(
            @RequestParam long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return relationService.listFollowings(userId, normalizeLimit(limit), Math.max(offset, 0));
    }

    @GetMapping("/followers")
    public List<RelationUserResponse> followers(
            @RequestParam long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return relationService.listFollowers(userId, normalizeLimit(limit), Math.max(offset, 0));
    }

    @GetMapping("/counter")
    public RelationCountsResponse counter(@RequestParam long userId) {
        return relationService.getCounts(userId);
    }

    private int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    }
}
