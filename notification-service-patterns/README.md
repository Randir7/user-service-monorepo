# notification-service

Микросервис почтовых уведомлений: читает события жизненного цикла пользователя
из Kafka и отправляет письма; предоставляет API прямой отправки.
Данных не хранит (без базы данных).

## Как работает

```
Kafka: user-created ──▶ 
                        ├─▶ UserEventConsumer ─
Kafka: user-deleted ───▶ ┘                     │
                                               ├─▶ NotificationService ─▶ EmailSender ─▶ SMTP
POST /api/v1/notifications ─▶ Controller ──────┘
```

Тексты писем (шаблоны фиксированы):

| Событие / тип | Тема | Текст |
|---|---|---|
| `USER_CREATED` | Регистрация на сайте | Здравствуйте! Ваш аккаунт на сайте *<site-name>* был успешно создан. |
| `USER_DELETED` | Удаление аккаунта | Здравствуйте! Ваш аккаунт был удалён. |

Название сайта и адрес отправителя задаются в `application.yml`
(`app.notification.site-name`, `app.notification.from`).

## Что должно быть запущено

| Программа | Назначение | Порты |
|---|---|---|
| Docker: Kafka | брокер событий | 9092 |
| Docker: Mailpit | приём писем и веб-просмотр | 1025 (SMTP), 8025 (UI) |

Требуемое ПО: JDK 17, Maven, Docker. **PostgreSQL не нужен.**

### Шаг 1. Инфраструктура

Kafka и Mailpit поднимаются compose-файлом из репозитория `user-service`:

```bash
cd <путь к репозиторию user-service>
docker compose up -d
```

### Шаг 2. Запуск сервиса

```bash
mvn spring-boot:run
```

Сервис доступен на **http://localhost:8081** (порт отличается от user-service).

### Шаг 3. Просмотр писем

Веб-интерфейс Mailpit: **http://localhost:8025** — все «отправленные» письма
появляются здесь.

## Сценарий 1: письма из событий Kafka

Создать или удалить пользователя в user-service — письмо придёт автоматически:

```bash
curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" -d "{\"name\":\"Ivan\",\"email\":\"ivan@example.com\",\"age\":28}"
curl -X DELETE http://localhost:8080/api/v1/users/1
```

Проверка: в Mailpit появятся письма «Регистрация на сайте» и
«Удаление аккаунта».

## Сценарий 2: прямая отправка через API

| Метод | Путь | Тело запроса | Успех | Описание |
|---|---|---|---|---|
| POST | `/api/v1/notifications` | email + тип | 200 | отправить уведомление |

```bash
curl -i -X POST http://localhost:8081/api/v1/notifications -H "Content-Type: application/json" -d "{\"email\":\"anna@example.com\",\"eventType\":\"USER_CREATED\"}"
```

```bash
curl -i -X POST http://localhost:8081/api/v1/notifications -H "Content-Type: application/json" -d "{\"email\":\"anna@example.com\",\"eventType\":\"USER_DELETED\"}"
```

Ответ `200 OK` означает: письмо передано SMTP-серверу (видно в Mailpit).

### Ошибки API

| Статус | Причина |
|---|---|
| 400 | невалидный email, отсутствующий или неизвестный `eventType`, битый JSON |
| 503 | почтовый сервер (SMTP) недоступен |
| 500 | внутренняя ошибка |

Формат тела ошибки совпадает с user-service (`ErrorResponse`).

## Ключевая конфигурация (`application.yml`)

| Свойство | Значение по умолчанию | Назначение |
|---|---|---|
| `server.port` | 8081 | порт REST API |
| `spring.kafka.bootstrap-servers` | localhost:9092 | адрес брокера |
| `spring.kafka.consumer.group-id` | notification-service | группа консьюмеров |
| `spring.kafka.consumer.auto-offset-reset` | earliest | чтение истории с начала |
| `spring.mail.host` / `spring.mail.port` | localhost / 1025 | SMTP Mailpit |
| `app.kafka.topic-user-created` / `-deleted` | user-created / user-deleted | топики-источники |
| `app.notification.site-name` | My Super Site | название сайта в письме |
| `app.notification.from` | no-reply@my-super-site.local | адрес отправителя |

## Отказоустойчивость потребления

Сбой обработки (например, недоступность SMTP) приводит к 3 повторам с паузой
2 секунды; после исчерпания сообщение уходит в Dead Letter Topic
(`user-created.DLT`, `user-deleted.DLT`) и не блокирует поток потребления.

## Тесты

```bash
mvn test
```

Требуется запущенный Docker (Testcontainers Kafka). Почта в тестах эмулируется
GreenMail внутри JVM, dev-Mailpit не затрагивается.
