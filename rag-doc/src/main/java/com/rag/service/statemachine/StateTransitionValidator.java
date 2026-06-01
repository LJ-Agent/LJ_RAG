package com.rag.service.statemachine;

import com.rag.common.enums.DocumentStatus;

/**
 * 状态转移规则验证器。
 */
public class StateTransitionValidator {

    public void validate(DocumentStatus from, DocumentStatus to) {
        if (!from.canTransitTo(to)) {
            throw new IllegalStateException(
                    String.format("非法状态转移: %s -> %s", from.name(), to.name()));
        }
    }
}
