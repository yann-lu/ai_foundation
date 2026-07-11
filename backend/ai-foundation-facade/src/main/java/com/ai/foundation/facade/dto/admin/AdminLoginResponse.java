package com.ai.foundation.facade.dto.admin;

import lombok.Data;

@Data
public class AdminLoginResponse {

    private String token;
    private String username;
    private String nickname;

    public AdminLoginResponse(String token, String username, String nickname) {
        this.token = token;
        this.username = username;
        this.nickname = nickname;
    }
}
