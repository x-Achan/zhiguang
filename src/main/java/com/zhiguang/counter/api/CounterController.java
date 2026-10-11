package com.zhiguang.counter.api;

import com.zhiguang.auth.token.JwtService;
import com.zhiguang.counter.api.dto.CounterActionResponse;
import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.service.CounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/counter")
@RequiredArgsConstructor
public class CounterController {

    private final JwtService jwtService;
    private final CounterService counterService;

    @PostMapping("/{postId}/like")
    public CounterActionResponse like(@PathVariable("postId") long postId,
                                      @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        return counterService.toggle(CounterType.LIKE,postId,userId,true);
    }
    @DeleteMapping("/{postId}/like")
    public CounterActionResponse unlike(@PathVariable("postId") long postId,
                                      @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        return counterService.toggle(CounterType.LIKE,postId,userId,false);
    }
    @PostMapping("/{postId}/fav")
    public CounterActionResponse fav(@PathVariable("postId") long postId,
                                      @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        return counterService.toggle(CounterType.FAV,postId,userId,true);
    }
    @DeleteMapping("/{postId}/fav")
    public CounterActionResponse unfav(@PathVariable("postId") long postId,
                                      @AuthenticationPrincipal Jwt jwt){
        long userId = jwtService.extractUserId(jwt);
        return counterService.toggle(CounterType.FAV,postId,userId,false);
    }
}
