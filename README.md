# LearnEnglishWordsTelegramBot

Телеграм-бот для изучения английских слов. Проект сделан как курсовая работа по Kotlin и заодно как тренировка работы с Telegram Bot API и HTTP-запросами.

## Что умеет бот

- Отправлять пользователю английские слова для изучения
- Помогать повторять лексику в формате диалога с ботом
- Демонстрировать базовую логику Telegram-бота на Kotlin

(Конкретные команды и сценарии можно дополнить отдельно в разделе Commands, если будет нужно.)

## Технологический стек

- **Язык:** Kotlin (JVM), версия 2.1.0
- **JVM:** JDK 17 (настроен `jvmToolchain(17)`)
- **Сборка:** Gradle Kotlin DSL
- **Серилизация:**  
  - `kotlinx-serialization-json` — работа с JSON
- **Сеть:**  
  - `okhttp` — HTTP-клиент для запросов к Telegram Bot API и сторонним сервисам
- **Логирование:**  
  - `logback-classic`
- **Плагины Gradle:**
  - `application` — запуск бота как обычного JVM-приложения
  - `com.github.johnrengelman.shadow` — сборка fat-jar для удобного деплоя

Точка входа в приложение: класс `TelegramKt` (настроено в `application.mainClass`).

## Требования

- Установленный **JDK 17**
- Доступ к Интернету
- Токен Telegram-бота, выданный через `@BotFather`  
  (как именно токен передаётся — см. реализацию в коде: переменная окружения, файл конфигурации и т.п.)

## Как запустить

### Клонирование репозитория

    git clone https://github.com/COBaOrNotCOBa/LearnEnglishWordsTelegramBot.git
    cd LearnEnglishWordsTelegramBot

### Настройка токена бота

Укажите токен Telegram-бота в том месте, где он ожидается кодом  
(например, через переменные окружения или конфигурационный файл).

### Сборка fat-jar

Linux / macOS:

    ./gradlew shadowJar

Windows:

    gradlew.bat shadowJar

Готовый jar-файл будет в каталоге:

    build/libs/

обычно с суффиксом `-all.jar`.

### Запуск бота

Запуск через собранный jar:

    java -jar build/libs/<имя-jar>-all.jar

Запуск через Gradle Wrapper:

Linux / macOS:

    ./gradlew run

Windows:

    gradlew.bat run

## Статус проекта

Курсовая работа по Kotlin и Telegram Bot API. Подходит как пример простого бота для обучения английским словам и основы для дальнейших экспериментов. После этого проект был доработан под школьную программу.
