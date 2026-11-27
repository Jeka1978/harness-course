package com.harness.agent;

import com.harness.TestPermissionConfig;
import com.harness.context.Compactor;
import com.harness.context.ContextManager;
import com.harness.context.GitStatusProvider;
import com.harness.context.ProjectMemoryLoader;
import com.harness.context.SystemPromptBuilder;
import com.harness.tool.FileStateCache;
import com.harness.tool.TodoStore;
import com.harness.tool.Tool;
import com.harness.tool.ToolRegistry;

import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/** Сборка агента для тестов: без Spring-контекста и без сети. */
final class TestAgents {

    private TestAgents() {}

    static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperties(20, ".", true), tools);
    }

    static Agent agent(
            ChatModel model,
            RecordingEventPublisher events,
            AgentProperties properties,
            Tool<?>... tools) {
        TodoStore todos = new TodoStore(16_384);
        ToolRegistry registry =
                com.harness.TestPermissionConfig.registry(
                        List.of(tools),
                        events,
                        new FileStateCache(),
                        todos,
                        TestPermissionConfig.permissive(),
                        TestPermissionConfig.allowOnce());
        return new Agent.Builder(model, events, properties, new Compactor(model, events, 16_384))
                .tools(registry)
                .systemPrompt(context(registry, properties).systemPrompt())
                .todos(todos)
                .build();
    }

    /**
     * Настоящий {@link ContextManager}, а не заглушка: весь контекст — обычные объекты с
     * конструкторами, и собрать их руками дешевле, чем объяснять подмену.
     */
    private static ContextManager context(ToolRegistry registry, AgentProperties properties) {
        return new ContextManager(
                new GitStatusProvider(properties),
                new ProjectMemoryLoader(properties),
                new SystemPromptBuilder(registry, properties),
                properties);
    }
}
