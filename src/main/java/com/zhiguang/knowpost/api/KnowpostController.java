package com.zhiguang.knowpost.api;

import com.zhiguang.auth.token.JwtService;
import com.zhiguang.knowpost.api.dto.*;
import com.zhiguang.knowpost.service.KnowPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/knowposts")
@RequiredArgsConstructor
public class KnowpostController {

    private final KnowPostService knowPostService;
    private final JwtService jwtService;

    @PostMapping("/draft")
    public KnowPostDraftCreateResponse createDraft(@AuthenticationPrincipal Jwt jwt){
        // 1.解析 jwt 获取userId
        long userId = jwtService.extractUserId(jwt);
        // 2.创建草稿，返回知文id
        long id = knowPostService.createDraft(userId);
        return new KnowPostDraftCreateResponse(String.valueOf(id));
    }

    @PatchMapping("/{id}/patch")
    public ResponseEntity<Void> updateMetaData(@PathVariable("id") long id,
                                               @Valid @RequestBody KnowPostPatchRequest request,
                                               @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        knowPostService.updateMetadata(userId,id,request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id,
                                       @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        knowPostService.delete(userId,id);
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/top")
    public ResponseEntity<Void> patchTop(@PathVariable("id") long id,
                                         @AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody KnowPostTopPatchRequest request){

        long userId = jwtService.extractUserId(jwt);
        knowPostService.updateTop(userId,id,request.isTop());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/visible")
    public ResponseEntity<Void> patchVisible(@PathVariable("id") long id,
                                             @AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody KnowPostVisibilityPatchRequest request){

        long userId = jwtService.extractUserId(jwt);
        knowPostService.updateVisibility(userId,id,request.visible());
        return ResponseEntity.noContent().build();
    }
    // 上传文件之后 回传确认
    @PostMapping("/{id}/content/confirm")
    public ResponseEntity<Void> confirmContent(@PathVariable("id") long id,
                                               @Valid @RequestBody KnowPostContentConfirmRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        knowPostService.confirmContent(userId, id, request.objectKey(), request.etag(), request.size(), request.sha256());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable("id") long id,
                                        @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        knowPostService.publish(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/detail")
    public KnowPostDetailResponse getDetail(@PathVariable("id") long id,
                                            @AuthenticationPrincipal Jwt jwt){
        Long userId =(jwt == null) ? null : jwtService.extractUserId(jwt);
        return knowPostService.getDetail(id,userId);
    }

}
