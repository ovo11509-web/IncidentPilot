package com.nageoffer.ai.ragent.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.dao.entity.UserDO;
import com.nageoffer.ai.ragent.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SaTokenStpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        String loginIdStr = loginId.toString();
        if (!StrUtil.isNumeric(loginIdStr)) {
            return Collections.emptyList();
        }
        UserDO user = userMapper.selectById(Long.parseLong(loginIdStr));
        if (user == null || StrUtil.isBlank(user.getRole())) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}
