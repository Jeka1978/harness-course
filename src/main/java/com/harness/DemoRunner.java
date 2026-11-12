package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сценарий текущего урока. Единственный файл, который меняется «до» урока —
 * всё остальное мы дописываем по ходу.
 *
 * <p>Урок 7: просим запустить тесты. Агент знает проект, читает и правит
 * файлы, но выполнить команду ему пока нечем.
 */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) throws IOException {
        editsScene();
//        System.out.println(agent.run("Запусти тесты проекта и скажи, всё ли зелено."));
    }



    private void editsScene() throws IOException {
        // Новый файл можно писать без предварительного Read, поэтому прошлую копию убираем.
        Files.deleteIfExists(Path.of("target/lesson7-note.txt"));
        // В DEFAULT запись спрашивает, ответь y. В ACCEPT_EDITS запись внутри проекта проходит молча.
        System.out.println("\n=== БОНУС. Запись файла: DEFAULT спрашивает, ACCEPT_EDITS — нет ===");
        System.out.println(agent.run("""
                Создай инструментом Write новый файл target/lesson7-note.txt
                с одной строкой: LESSON7_EDITS
                Другие инструменты не нужны. В конце сообщи, получилось ли записать файл.
                """));
    }
}
