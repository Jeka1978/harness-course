package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Сценарий урока 11. */
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
                Миссия: допиши метод PhoneBook.find так, чтобы он искал по всем вариантам названий полей.
                Справочник лежит в папке phonebook: west.json, mountain.json, midwest.json, south.json, northeast.json. \
                Данные чужие и не нормализованы: одно и то же поле в разных карточках называется по-разному.
                Сначала составь план. Изучение каждого файла — отдельный пункт, \
                 выпиши дословно, как в файле, все различные ключи, которые встречаются в карточках, к каждому — \
                один пример значения, а потом отдельно перечислит, какие из этих ключей означают одно и то же. \
                Последний пункт — словарь синонимов и код find.
                Когда допишешь, проверь себя: запусти .\\mvnw.cmd test -Dgroups=mission -Dexcluded.groups= \
                -Dtest=PhoneBookMissionTest и, если тест красный, исправь код."""));
    }
}
