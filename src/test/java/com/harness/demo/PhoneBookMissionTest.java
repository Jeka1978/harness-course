package com.harness.demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Приёмка урока 11: проверяет поиск, который написал агент.
 *
 * <p>Запускается руками, сразу после демонстрации, пока мишень ещё поправлена:
 *
 * <pre>mvnw test -Dgroups=mission -Dexcluded.groups=</pre>
 *
 * <p>Каждый случай требует синонимов из своего региона: кто собрал словарь
 * не по всему справочнику, тот кого-то не найдёт.
 */
@Tag("mission")
class PhoneBookMissionTest {

    private static final Path PHONEBOOK = Path.of("phonebook");

    /** Сары Коннор во всех четырёх регионах, имена записаны по-разному. */
    @Test
    void findsEverySarahConnor() {
        assertThat(PhoneBook.find(PHONEBOOK, Map.of("first_name", "Sarah", "last_name", "Connor")))
                .hasSize(6);
    }

    /** Три Сары Коннор в Лос-Анджелесе, у одной город записан редким полем. */
    @Test
    void findsThreeSarahConnorsInLosAngeles() {
        assertThat(PhoneBook.find(PHONEBOOK,
                Map.of("first_name", "Sarah", "last_name", "Connor", "city", "Los Angeles")))
                .hasSize(3);
    }

    /** Телефон записан редким полем. */
    @Test
    void findsByPhoneUnderAnotherName() {
        List<Map<String, Object>> found = PhoneBook.find(PHONEBOOK, Map.of("phone", "213-555-0129"));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst()).containsValue("Reese");
    }

    /** Индекс записан у двоих, и у каждого своим полем. */
    @Test
    void findsByZipUnderEveryName() {
        assertThat(PhoneBook.find(PHONEBOOK, Map.of("zip", "02134"))).hasSize(2);
    }
}
