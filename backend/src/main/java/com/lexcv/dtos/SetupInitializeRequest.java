package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupInitializeRequest {
    private String clientName;
    private String logo;
    private String adminEmail;
    private String adminPassword;
}
