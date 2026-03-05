package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isfollowed}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isfollowed") Boolean isFollowed) {
        return followService.follow(id, isFollowed);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") String id) {
        Long followUserId = parseFollowUserId(id);
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") String id) {
        Long targetUserId = parseFollowUserId(id);
        if (targetUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        return followService.followCommons(targetUserId);
    }

    private Long parseFollowUserId(String id) {
        if (StrUtil.isBlank(id) || "undefined".equalsIgnoreCase(id)) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
