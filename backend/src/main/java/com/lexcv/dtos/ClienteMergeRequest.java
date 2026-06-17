package com.lexcv.dtos;

import java.util.UUID;

public record ClienteMergeRequest(
        UUID primaryId,
        UUID secondaryId
) {}

