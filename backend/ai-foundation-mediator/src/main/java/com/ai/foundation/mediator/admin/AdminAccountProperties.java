package com.ai.foundation.mediator.admin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "agent.admin")
public class AdminAccountProperties {

    private List<AdminAccount> accounts = new ArrayList<>();

    @Data
    public static class AdminAccount {
        private String username;
        private String password;
        private String nickname;
    }
}
