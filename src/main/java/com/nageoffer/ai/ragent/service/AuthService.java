package com.nageoffer.ai.ragent.service;

import com.nageoffer.ai.ragent.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.controller.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginRequest requestParam);

    void logout();
}
