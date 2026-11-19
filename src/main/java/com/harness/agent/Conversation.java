package com.harness.agent;

import com.harness.context.TokenEstimator;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.Usage;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private final String systemPrompt;
    private final List<Message> messages = new ArrayList<>();

    /** Сколько токенов насчитала модель, когда отвечала последний раз: системный промпт, описания инструментов, история и сам ответ. */
    private int countedTokens;

    /** Сколько сообщений было тогда в истории. Всё, что пришло после, модель ещё не считала. */
    private int countedMessages;

    public Conversation(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        // Модель ещё ничего не считала — начинаем с оценки.
        this.countedTokens = TokenEstimator.estimate(systemPrompt);
    }

    public void addUserMessage(String userMessage) {
        messages.add(new UserMessage(userMessage));
    }

    /**
     * Служебная врезка — отдельной репликой, как в Claude Code. Роли «система» посреди
     * разговора нет, поэтому говорим голосом человека, а тег отличает врезку от просьбы.
     */
    public void addSystemReminder(String text) {
        messages.add(new UserMessage("<system-reminder>\n" + text + "\n</system-reminder>"));
    }

    public void addAssistantMessage(AssistantMessage assistantMessage) {
        messages.add(assistantMessage);
    }

    public List<Message> toPromptMessages() {
        ArrayList<Message> prompt = new ArrayList<>(messages.size() + 1);
        prompt.add(new SystemMessage(systemPrompt));
        prompt.addAll(messages);
        return prompt;
    }

    public void addToolResponses(ToolResponseMessage toolResponseMessage) {
        messages.add(toolResponseMessage);
    }

    /** Модель вернула счёт токенов вместе с ответом. Ответ уже в истории. */
    public void countedByModel(Usage usage) {
        Integer total = usage.getTotalTokens();
        if (total == null || total == 0) return; // счёта нет — остаёмся на оценке
        countedTokens = total;
        countedMessages = messages.size();
    }

    /**
     * Сколько места разговор занимает в окне. Как в Claude Code: что посчитала модель — точно,
     * что пришло после её ответа — оценкой.
     */
    public int tokens() {
        return countedTokens + messages.subList(countedMessages, messages.size()).stream()
                .mapToInt(TokenEstimator::estimate)
                .sum();
    }

}
