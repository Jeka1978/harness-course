package com.harness.agent;

import com.harness.agent.event.AgentEvent;
import com.harness.tool.ToolRegistry;
import com.harness.tool.ToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.context.ApplicationEventPublisher;

public class RepeatedCallGuard {

    static final int LIMIT = 3;

    private static final String REFUSAL = """
            Your last tool call was blocked and NOT executed:
            %s %s
            Last result: %s
            Do not send this call again. Your next tool call must differ from it in tool name or arguments.
            If no different call can help, answer the user without calling tools.""";

    private record Call(String toolName, String arguments) {

    }

    private Call lastCall;
    private int callCount;
    private ToolResult lastResult;

    public ToolResult execute(ToolRegistry toolRegistry, AssistantMessage.ToolCall toolCall, ApplicationEventPublisher publisher) {
        Call call = new Call(toolCall.name(), toolCall.arguments());
        if (!call.equals(lastCall)) {
            lastCall = call;
            callCount = 0;
        }
        callCount++;
        if (isBlocked()) {
            publisher.publishEvent(new AgentEvent.RepeatedCallBlocked(toolCall.name(), callCount));

            return ToolResult.error(REFUSAL.formatted(toolCall.name(), toolCall.arguments(), lastResult.content()));
        }
        lastResult = toolRegistry.execute(toolCall.name(), toolCall.arguments());
        return lastResult;

    }

    boolean isBlocked() {
        return callCount > LIMIT;
    }


}
