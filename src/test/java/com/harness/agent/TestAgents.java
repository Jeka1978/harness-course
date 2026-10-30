package com.harness.agent;

import com.harness.context.ContextManager;
import com.harness.context.GitStatusProvider;
import com.harness.context.ProjectMemoryLoader;
import com.harness.context.SystemPromptBuilder;
import com.harness.tool.FileStateCache;
import com.harness.tool.SpringAiToolAdapter;
import com.harness.tool.Tool;
import com.harness.tool.ToolRegistry;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;

/** Сборка агента для тестов: без Spring-контекста и без сети. */
final class TestAgents {

    private TestAgents() {}

    static Agent agent(ChatModel model, RecordingEventPublisher events, Tool<?>... tools) {
        return agent(model, events, new AgentProperties(20, ".", true), tools);
    }

    static Agent agent(
            ChatModel model, RecordingEventPublisher events, AgentProperties properties, Tool<?>... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools), events, new FileStateCache());
        return new Agent(model, events, registry, new SpringAiToolAdapter(registry), properties,
                context(registry, properties));
    }

    /**
     * Настоящий {@link ContextManager}, а не заглушка: весь контекст — обычные объекты
     * с конструкторами, и собрать их руками дешевле, чем объяснять подмену.
     */
    private static ContextManager context(ToolRegistry registry, AgentProperties properties) {
        return new ContextManager(
                new GitStatusProvider(properties),
                new ProjectMemoryLoader(properties),
                new SystemPromptBuilder(registry, properties),
                properties);
    }
}
