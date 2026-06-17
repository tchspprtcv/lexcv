package com.lexcv.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetupStatusResponse {
    private final boolean initialized;
}
