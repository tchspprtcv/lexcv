package com.lexcv.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetupInitializeResponse {
    private final boolean initialized;
    private final String message;
}
