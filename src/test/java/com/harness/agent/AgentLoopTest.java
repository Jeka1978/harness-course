package com.harness.agent;

import static com.harness.agent.TestAgents.agent;
import static org.assertj.core.api.Assertions.assertThat;

import com.harness.agent.event.AgentEvent;
import com.harness.tool.Tool;
import com.harness.tool.ToolContext;
import com.harness.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * Тесты агентского цикла — сердце урока 3.
 *
 * <p>На живой модели такое не проверить: она недетерминирована и медленна.
 * {@link ScriptedChatModel} задаёт сценарий «первым ходом вызови инструмент,
 * вторым ответь текстом», и цикл становится обычным кодом, который проверяется
 * за миллисекунды.
 */
class AgentLoopTest {

    private final RecordingEventPublisher publisher = new RecordingEventPublisher();

    /** Инструмент-шпион: записывает, с чем его позвали. */
    static class SpyTool implements Tool<SpyTool.Input> {
        record Input(String value) {}

        final List<String> calls = new ArrayList<>();
        private final ToolResult answer;

        SpyTool(ToolResult answer) {
            this.answer = answer;
        }

        @Override
        public String name() {
            return "Spy";
        }

        @Override
        public String description() {
            return "test tool";
        }

        @Override
        public Class<Input> inputType() {
            return Input.class;
        }

        @Override
        public String renderToolUseMessage(Input input) {
            return input.value();
        }

        @Override
        public ToolResult call(Input input, ToolContext context) {
            calls.add(input.value());
            return answer;
        }
    }

    private static AssistantMessage toolCall(String id, String name, String argumentsJson) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, argumentsJson)))
                .build();
    }

    private static ToolResponseMessage toolMessageOf(List<Message> request) {
        return (ToolResponseMessage) request.stream()
                .filter(message -> message.getMessageType() == MessageType.TOOL)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void выполняетИнструментИВозвращаетсяКМодели() {
        SpyTool tool = new SpyTool(ToolResult.ok("42"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"pom.xml\"}"))
                .then(new AssistantMessage("Версия 25."));

        String answer = agent(model, publisher, tool).run("какая версия?");

        assertThat(tool.calls).containsExactly("pom.xml");
        assertThat(answer).isEqualTo("Версия 25.");
        assertThat(model.callCount()).as("два обращения к модели: до и после инструмента").isEqualTo(2);
    }

    @Test
    void результатИнструментаУезжаетВИсториюДляМодели() {
        SpyTool tool = new SpyTool(ToolResult.ok("java.version = 25"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"pom.xml\"}"))
                .then(new AssistantMessage("Готово."));

        agent(model, publisher, tool).run("проверь");

        // Результат привязан к id вызова, а не к имени: за один ход модель может
        // попросить три чтения, и различить их она может только по id.
        ToolResponseMessage toolMessage = toolMessageOf(model.lastPrompt().getInstructions());
        assertThat(toolMessage.getResponses()).hasSize(1);
        assertThat(toolMessage.getResponses().getFirst().id()).isEqualTo("call-1");
        assertThat(toolMessage.getResponses().getFirst().responseData()).isEqualTo("java.version = 25");
    }

    @Test
    void ошибкаИнструментаВозвращаетсяМоделиАНеЛоматАгента() {
        SpyTool tool = new SpyTool(ToolResult.error("файл не найден: nope.txt"));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "Spy", "{\"value\":\"nope.txt\"}"))
                .then(new AssistantMessage("Такого файла нет."));

        String answer = agent(model, publisher, tool).run("прочитай nope.txt");

        assertThat(answer).isEqualTo("Такого файла нет.");
        assertThat(toolMessageOf(model.lastPrompt().getInstructions()).getResponses().getFirst().responseData())
                .contains("файл не найден");
    }

    @Test
    void несколькоВызововЗаОдинХод() {
        SpyTool tool = new SpyTool(ToolResult.ok("ok"));
        // Независимые вызовы модель присылает пачкой в одном сообщении, а не по очереди.
        ScriptedChatModel model = new ScriptedChatModel()
                .then(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                new AssistantMessage.ToolCall("a", "function", "Spy", "{\"value\":\"one\"}"),
                                new AssistantMessage.ToolCall("b", "function", "Spy", "{\"value\":\"two\"}")))
                        .build())
                .then(new AssistantMessage("Оба прочитаны."));

        // Текст задания здесь декоративный: подставная модель его не читает, она отдаёт
        // сценарий. Предмет проверки — что цикл сделает с одним сообщением, где вызова два.
        agent(model, publisher, tool).run("прочитай оба");

        // Оба и в том же порядке. Цикл, который взял бы только getToolCalls().getFirst(),
        // прошёл бы все тесты выше и терял половину работы на живой модели.
        assertThat(tool.calls).containsExactly("one", "two");
    }

    @Test
    void неизвестныйИнструментНеРоняетАгента() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "НетТакого", "{}"))
                .then(new AssistantMessage("Извини."));

        String answer = agent(model, publisher).run("позови несуществующее");

        assertThat(answer).isEqualTo("Извини.");
        assertThat(toolMessageOf(model.lastPrompt().getInstructions()).getResponses().getFirst().responseData())
                .contains("нет инструмента");
    }

    @Test
    void blocksRepeatedCallAndLetsAgentTryAnotherStep() {
        SpyTool tool = new SpyTool(ToolResult.error("класс не найден"));
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 1; i <= 3; i++) {
            model.then(toolCall("same-" + i, "Spy", "{\"value\":\"missing\"}"));
        }
        model.then(AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("same-4", "function", "Spy", "{\"value\":\"missing\"}"),
                new AssistantMessage.ToolCall("still-same", "function", "Spy", "{\"value\":\"missing\"}"),
                new AssistantMessage.ToolCall("different-in-batch", "function", "Spy", "{\"value\":\"in batch\"}")))
                .build());
        model.then(toolCall("fresh-after-other-call", "Spy", "{\"value\":\"missing\"}"));
        model.then(new AssistantMessage("Класс не найден, поиск остановлен."));

        String answer = agent(model, publisher, tool).run("найди класс");

        assertThat(tool.calls).containsExactly("missing", "missing", "missing", "in batch", "missing");
        assertThat(answer).isEqualTo("Класс не найден, поиск остановлен.");
        assertThat(publisher.events()).filteredOn(AgentEvent.RepeatedCallBlocked.class::isInstance)
                .containsExactly(new AgentEvent.RepeatedCallBlocked("Spy", 4), new AgentEvent.RepeatedCallBlocked("Spy", 5));

        List<Message> afterBlock = model.receivedPrompts().get(4).getInstructions();
        ToolResponseMessage responses = (ToolResponseMessage) afterBlock.get(afterBlock.size() - 2);
        assertThat(afterBlock.getLast().getMessageType()).isEqualTo(MessageType.USER);
        assertThat(afterBlock.getLast().getText()).isEqualTo(Agent.BLOCK_NOTICE);
        assertThat(responses.getResponses()).extracting(ToolResponseMessage.ToolResponse::id)
                .containsExactly("same-4", "still-same", "different-in-batch");
        assertThat(responses.getResponses().subList(0, 2))
                .extracting(ToolResponseMessage.ToolResponse::responseData)
                .allSatisfy(refusal -> assertThat(refusal)
                        .contains("Your last tool call was blocked and NOT executed", "класс не найден"));
    }

    @Test
    void keepsRejectingTheSameCallWithoutEndingTurn() {
        SpyTool tool = new SpyTool(ToolResult.error("файл не найден"));
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 1; i <= 5; i++) {
            model.then(toolCall("read-" + i, "Spy", "{\"value\":\"missing\"}"));
        }
        model.then(new AssistantMessage("Код не найден."));

        String answer = agent(model, publisher, tool).run("найди код");

        assertThat(answer).isEqualTo("Код не найден.");
        assertThat(model.callCount()).isEqualTo(6);
        assertThat(tool.calls).containsExactly("missing", "missing", "missing");
        assertThat(publisher.events()).filteredOn(AgentEvent.RepeatedCallBlocked.class::isInstance)
                .containsExactly(new AgentEvent.RepeatedCallBlocked("Spy", 4), new AgentEvent.RepeatedCallBlocked("Spy", 5));
        assertThat(publisher.events()).noneMatch(AgentEvent.TurnLimitReached.class::isInstance);

        List<Message> history = model.lastPrompt().getInstructions();
        assertThat(history).filteredOn(message -> message.getMessageType() == MessageType.USER)
                .extracting(Message::getText)
                .filteredOn(text -> text.startsWith("TOOL CALL BLOCKED"))
                .containsExactly(Agent.BLOCK_NOTICE, Agent.BLOCK_NOTICE);
        assertThat(history.stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .filter(response -> List.of("read-4", "read-5").contains(response.id()))
                .toList())
                .extracting(ToolResponseMessage.ToolResponse::responseData)
                .hasSize(2)
                .allSatisfy(refusal -> assertThat(refusal).contains("Your last tool call was blocked"));
    }

    @Test
    void canRetryAfterADifferentCall() {
        SpyTool tool = new SpyTool(ToolResult.ok("старое содержимое")) {
            @Override
            public ToolResult call(Input input, ToolContext context) {
                calls.add(input.value());
                if ("edit".equals(input.value())) {
                    return ToolResult.ok("файл изменён");
                }
                return ToolResult.ok(calls.contains("edit") ? "новое содержимое" : "старое содержимое");
            }
        };
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 1; i <= 4; i++) {
            model.then(toolCall("read-" + i, "Spy", "{\"value\":\"read\"}"));
        }
        model.then(AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("edit", "function", "Spy", "{\"value\":\"edit\"}"),
                new AssistantMessage.ToolCall("verify", "function", "Spy", "{\"value\":\"read\"}")))
                .build());
        model.then(new AssistantMessage("Перечитал файл после правки."));

        String answer = agent(model, publisher, tool).run("измени файл и проверь");

        assertThat(answer).isEqualTo("Перечитал файл после правки.");
        assertThat(tool.calls).containsExactly("read", "read", "read", "edit", "read");
        assertThat(publisher.events()).filteredOn(AgentEvent.RepeatedCallBlocked.class::isInstance)
                .containsExactly(new AgentEvent.RepeatedCallBlocked("Spy", 4));
        assertThat(toolMessageOf(model.lastPrompt().getInstructions()).getResponses().getFirst().responseData())
                .isEqualTo("старое содержимое");
        assertThat(model.lastPrompt().getInstructions()).filteredOn(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).last()
                .satisfies(message -> assertThat(message.getResponses().getLast().responseData())
                        .isEqualTo("новое содержимое"));
    }

    @Test
    void blocksFourthCallEvenWhenResultsChange() {
        SpyTool tool = new SpyTool(ToolResult.ok("unused")) {
            @Override
            public ToolResult call(Input input, ToolContext context) {
                calls.add(input.value());
                return ToolResult.ok("результат " + calls.size());
            }
        };
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 1; i <= 4; i++) {
            model.then(toolCall("call-" + i, "Spy", "{\"value\":\"same\"}"));
        }
        model.then(new AssistantMessage("Готово."));

        agent(model, publisher, tool).run("проверь несколько раз");

        assertThat(tool.calls).hasSize(3);
        assertThat(publisher.events()).filteredOn(AgentEvent.RepeatedCallBlocked.class::isInstance)
                .containsExactly(new AgentEvent.RepeatedCallBlocked("Spy", 4));
        assertThat(model.lastPrompt().getInstructions()).filteredOn(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).last()
                .satisfies(message -> assertThat(message.getResponses().getFirst().responseData())
                        .contains("Last result: результат 3"));
    }

    @Test
    void userCanRestartSameCallInANewTurn() {
        SpyTool tool = new SpyTool(ToolResult.error("класс не найден"));
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 1; i <= 3; i++) {
            model.then(toolCall("first-" + i, "Spy", "{\"value\":\"same\"}"));
        }
        model.then(new AssistantMessage("Остановился."));
        model.then(toolCall("second-1", "Spy", "{\"value\":\"same\"}"));
        model.then(new AssistantMessage("Перепроверил."));
        Agent agent = agent(model, publisher, tool);

        agent.run("найди класс");
        agent.run("попробуй ещё раз");

        assertThat(tool.calls).hasSize(4);
        assertThat(publisher.events()).noneMatch(AgentEvent.RepeatedCallBlocked.class::isInstance);
    }

    private static final String PLAN = """
            {"todos":[{"content":"прочитать файлы","status":"COMPLETED","activeForm":"Читаю файлы"},
                      {"content":"спросить формат","status":"IN_PROGRESS","activeForm":"Спрашиваю формат"},
                      {"content":"записать отчёт","status":"PENDING","activeForm":"Пишу отчёт"}]}""";

    /** Первый ход: план и вопрос человеку. Второй — его ответ указанной длины. */
    private List<String> userTextsAfterAnswer(int answerLength) {
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "TodoWrite", PLAN))
                .then(new AssistantMessage("В каком формате нужен отчёт?"))
                .then(new AssistantMessage("Понял."));
        Agent agent = agent(model, publisher, new com.harness.tool.impl.TodoWriteTool(publisher));

        agent.run("Прочитай файлы и запиши отчёт.");
        agent.run("x".repeat(answerLength));

        return model.lastPrompt().getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .toList();
    }

    /** План уже есть — реплика человека уходит как есть, и врезки за ней нет. */
    @Test
    void сПланомРепликаЧеловекаБезВрезки() {
        assertThat(userTextsAfterAnswer(13_000).getLast()).isEqualTo("x".repeat(13_000));
    }

    /** Плана нет — мягкое напоминание идёт отдельной репликой сразу за репликой человека. */
    @Test
    void безПланаМягкоеНапоминаниеОтдельнойРепликой() {
        ScriptedChatModel model = new ScriptedChatModel().then(new AssistantMessage("Понял."));

        agent(model, publisher, new com.harness.tool.impl.TodoWriteTool(publisher)).run("прочитай файлы");

        List<String> userTexts = model.lastPrompt().getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .toList();
        assertThat(userTexts).hasSize(2);
        assertThat(userTexts.getFirst()).isEqualTo("прочитай файлы");
        assertThat(userTexts.getLast())
                .startsWith("<system-reminder>\nThe TodoWrite tool")
                .endsWith("</system-reminder>");
    }

    /**
     * План есть, а ответ инструмента большой — план идёт отдельной репликой после ответов.
     * Сам ответ не трогаем: Ollama при переполнении выкидывает начало разговора, и хвост
     * ответа без него модель читает как данные. Отдельную реплику — как ход, на который надо ответить.
     */
    @Test
    void напоминаниеОПланеОтдельнойРепликойПослеОтветов() {
        SpyTool tool = new SpyTool(ToolResult.ok("x".repeat(14_000)));
        ScriptedChatModel model = new ScriptedChatModel()
                .then(toolCall("call-1", "TodoWrite", PLAN))
                .then(toolCall("call-2", "Spy", "{\"value\":\"big.txt\"}"))
                .then(new AssistantMessage("Готово."));

        agent(model, publisher, tool, new com.harness.tool.impl.TodoWriteTool(publisher)).run("прочитай big.txt");

        List<Message> request = model.lastPrompt().getInstructions();
        Message last = request.getLast();
        ToolResponseMessage beforeLast = (ToolResponseMessage) request.get(request.size() - 2);
        assertThat(beforeLast.getResponses().getFirst().responseData()).isEqualTo("x".repeat(14_000));
        assertThat(last.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(last.getText())
                .startsWith("<system-reminder>\nПомни о плане")
                .contains("2. [IN_PROGRESS] спросить формат")
                .endsWith("</system-reminder>");
    }
}
