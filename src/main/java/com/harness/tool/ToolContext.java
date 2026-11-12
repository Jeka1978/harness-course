package com.harness.tool;

import com.harness.permission.OrdersKeeper;
import com.harness.permission.ToolPermissionContext;
import org.springframework.stereotype.Component;

@Component
public record ToolContext(FileStateCache readFileState, OrdersKeeper ordersKeeper) {
    public ToolPermissionContext permissions() {
        return ordersKeeper.snapshot();
    }
}
