package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Сценарий текущего урока. Единственный файл, который меняется «до» урока —
 * всё остальное мы дописываем по ходу.
 *
 * <p>Урок 6: спрашиваем то, что агент обязан знать до первого вопроса, —
 * где он находится и что это за проект. В начале урока он пойдёт это искать
 * инструментами и ответит неверно; в конце ответит сразу, не сделав ни одного
 * вызова.
 */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) {
        System.out.println(agent.run("""
                На какой ветке git мы сейчас работаем и есть ли незакоммиченные изменения?
                И что это вообще за проект? Ответь коротко."""));
    }
}
