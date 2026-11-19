package com.harness.agent;

import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private final String systemPrompt;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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
}




