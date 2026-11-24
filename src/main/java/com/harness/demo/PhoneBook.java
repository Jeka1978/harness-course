package com.harness.demo;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Мишень урока 11: поиск по чужому телефонному справочнику.
 *
 * <p>Справочник лежит в папке {@code phonebook}, по файлу на регион. Каждый файл —
 * JSON-массив плоских карточек. Данные чужие и не нормализованы: одно и то же поле
 * в разных карточках называется по-разному, например {@code phone} и {@code phone_number}.
 * Привести их к одному виду нельзя — искать приходится по всем названиям сразу.
 *
 * <p>Как и {@link TargetPractice}, после демонстрации возвращается на место:
 *
 * <pre>git checkout -- src/main/java/com/harness/demo/PhoneBook.java</pre>
 */
public final class PhoneBook {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Словарь синонимов: каноническое поле → все его названия в справочнике. Пока пуст — заполни. */
    private static final Map<String, List<String>> SYNONYMS = Map.of();

    private PhoneBook() {
    }

    /**
     * Все карточки справочника, подходящие под критерии. Ищет по всем названиям из {@link #SYNONYMS}:
     * значение поля под любым его названием возвращает {@link #valueOf}.
     *
     * <p>{@code find(Path.of("phonebook"), Map.of("first_name", "Sarah", "last_name", "Connor"))}
     *
     * @param phonebook папка со справочником
     * @param criteria  поле → искомое значение. Поля: {@code first_name}, {@code last_name},
     *                  {@code phone}, {@code address}, {@code city}, {@code zip}. Карточка подходит,
     *                  если для каждого критерия {@code valueOf(card, поле)} равно искомому значению
     * @return подходящие карточки как есть, с их собственными названиями полей
     */
    public static List<Map<String, Object>> find(Path phonebook, Map<String, String> criteria) {
        throw new UnsupportedOperationException("мишень для урока 11: метод ещё не реализован");
    }

    /** Все карточки из всех файлов справочника, как они записаны. */
    private static List<Map<String, Object>> readCards(Path phonebook) {
        List<Map<String, Object>> cards = new ArrayList<>();
        try (Stream<Path> files = Files.list(phonebook)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                cards.addAll(JSON.readValue(Files.readString(file), new TypeReference<List<Map<String, Object>>>() {}));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cards;
    }

    /**
     * Значение поля в карточке, под каким бы названием из {@link #SYNONYMS} оно ни было записано.
     *
     * @param field каноническое поле, например {@code city}
     * @return значение или {@code null}, если поля в карточке нет ни под одним названием
     */
    private static Object valueOf(Map<String, Object> card, String field) {
        if (card.containsKey(field)) {
            return card.get(field);
        }
        for (String name : SYNONYMS.getOrDefault(field, List.of())) {
            if (card.containsKey(name)) {
                return card.get(name);
            }
        }
        return null;
    }
}
