package com.harness.agent;

import com.harness.context.Compactor;
import com.harness.context.ContextManager;
import com.harness.tool.TodoStore;
import com.harness.tool.ToolRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/** Как Spring собирает агентов: заготовка с общим и главный агент из неё. */
@Configuration
public class AgentConfiguration {

    /** Заготовка агента. Каждый раз новая — как {@code ChatClient.Builder} в Spring AI: её дособирают и выбрасывают. */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent.Builder agentBuilder(ChatModel chatModel, ApplicationEventPublisher publisher,
                                      AgentProperties properties, Compactor compactor) {
        return new Agent.Builder(chatModel, publisher, properties, compactor);
    }

    /** Главный агент: все инструменты, системный промпт проекта, общий план. */
    @Bean
    @Primary
    public Agent agent(Agent.Builder agentBuilder, ToolRegistry toolRegistry, ContextManager contextManager, TodoStore todos) {
        return agentBuilder
                .tools(toolRegistry)
                .systemPrompt(contextManager.systemPrompt())
                .todos(todos)
                .build();
    }
}
