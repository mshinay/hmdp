package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
}
