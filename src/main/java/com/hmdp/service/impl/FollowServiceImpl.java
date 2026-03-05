package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollowed) {
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        String key = FOLLOW_KEY + userId;

        if (Boolean.TRUE.equals(isFollowed)) {
            int count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
            if (count > 0) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            if (!success) {
                return Result.fail("关注失败");
            }
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            return Result.ok();
        }

        boolean success = remove(query().eq("user_id", userId).eq("follow_user_id", followUserId));
        if (!success) {
            return Result.fail("取关失败");
        }
        stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        String key = FOLLOW_KEY + userId;
        Boolean member = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        if (Boolean.TRUE.equals(member)) {
            return Result.ok(Boolean.TRUE);
        }
        int count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        if (count > 0) {
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            return Result.ok(Boolean.TRUE);
        }
        return Result.ok(Boolean.FALSE);
    }

    @Override
    public Result followCommons(Long targetUserId) {
        if (targetUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        if (userId.equals(targetUserId)) {
            return Result.ok(Collections.emptyList());
        }

        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + targetUserId;
        Set<String> commonIds = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (commonIds == null || commonIds.isEmpty()) {
            Boolean hasKey1 = stringRedisTemplate.hasKey(key1);
            Boolean hasKey2 = stringRedisTemplate.hasKey(key2);
            if (Boolean.FALSE.equals(hasKey1)) {
                loadFollowsToRedis(userId, key1);
            }
            if (Boolean.FALSE.equals(hasKey2)) {
                loadFollowsToRedis(targetUserId, key2);
            }
            commonIds = stringRedisTemplate.opsForSet().intersect(key1, key2);
            if (commonIds == null || commonIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
        }

        List<Long> ids = commonIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(item -> BeanUtil.copyProperties(item, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    private void loadFollowsToRedis(Long userId, String key) {
        List<Follow> follows = query().eq("user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            return;
        }
        String[] ids = follows.stream()
                .map(follow -> follow.getFollowUserId().toString())
                .toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, ids);
    }
}
