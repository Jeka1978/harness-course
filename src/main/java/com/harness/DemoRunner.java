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
 * <p>Урок 9: расследование в одном разговоре. Человек вставляет карточки подозреваемых,
 * просит прочитать отчёт, потом даёт задание с планом. След ведёт к бумагам, которых
 * нет ни в одном файле: человек приносит их, сканирует и вставляет текст в последнюю реплику.
 * Свободное место в окне тает от реплики к реплике.
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
        System.out.println(agent.run("""
                Начинаем расследование. Вот карточки доступа троих сотрудников. Это подозреваемые, они понадобятся до конца дела:

                Арнольд Айбэкович Восьмисотов, пропуск № 0388 — разработчик. Доступы: репозиторий, пароль deploy, VPN. pom.xml знает наизусть.
                Алиса Несировна Копайлот, пропуск № 0417 — руководитель релизов. Доступы: репозиторий, пароль deploy, ключ подписи релизов, VPN, склад техники.
                Джон Коннор, пропуск № 0452 — тестировщик. Доступы: репозиторий только на чтение, VPN. Пароля deploy не знает.

                В ночь на 7 ноября кто-то подложил в сборку чужой код через учётную запись deploy. Кто из троих мог это сделать?"""));

        System.out.println(agent.run("Прочитай incident/report.md и ответь коротко: что именно подложили и когда?"));

        System.out.println(agent.run("""
                Подозреваемых двое. Выясни, кто из них это сделал. Составь план и выполни его по шагам:
                1. Прочитай журнал входов в VPN: incident/logs/vpn.log.
                2. Выясни по нему, с какого ноутбука ночью 7 ноября входили под deploy.
                3. В incident/inventory.md узнай, за кем закреплён этот ноутбук.
                4. Проверь по incident/logs/ci.log, какая сборка ушла с этого ноутбука.
                5. Найди в папке incident календарь команды: где был хозяин ноутбука той ночью?
                6. Прочитай документы, на которые ссылается календарь, и те, на которые ссылаются они.
                7. Перечисли мне, каких бумаг не хватает, чтобы закончить дело, и где лежит каждая. Я принесу."""));

        // Листы бумажные: агенту их не найти. Человек приносит, сканирует и вставляет текст в реплику.
        StringBuilder scans = new StringBuilder("Принёс все три листа, отсканировал. Вот их текст:");
        for (String sheet : new String[] {"1-pom.txt", "2-reglament.txt", "3-plan.txt"}) {
            scans.append("\n\n").append(Files.readString(Path.of("scans", sheet)).strip());
        }
        System.out.println(agent.run(scans + "\n\nНу и кто это сделал?"));
    }
}
