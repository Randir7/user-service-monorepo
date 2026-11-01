# User Service

Микросервис управления пользователями с интеграцией Kafka для событий жизненного цикла.

## Архитектура

Сервис является частью микросервисной системы:
- **Config Server** — централизованное хранилище конфигураций
- **Eureka Server** — реестр сервисов (Service Discovery)
- **API Gateway** — единая точка входа
- **Notification Service** — потребитель событий Kafka

## Паттерны микросервисной архитектуры

### External Configuration (Config Server)
Все конфигурации хранятся в Config Server (`http://localhost:8888`). Сервис получает их при старте через `bootstrap.yml`.

**Локальный `application.yml`:**
```yaml
server:
  port: 8081

spring:
  application:
    name: user-service