package com.shrirang.distributed_promptforge.common_lib.event;

public record EmailEvent(
        String to,
        String subject,
        String body
) {}
