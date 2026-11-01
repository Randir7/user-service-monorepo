package com.github.randir7.notificationservice.service;

/**
 * Типы почтовых уведомлений, поддерживаемых сервисом.
 *
 * <p>Каждая константа хранит тему письма и шаблон тела. Формулировки
 * соответствуют требованию задачи:
 * <ul>
 *   <li>USER_CREATED — «Здравствуйте! Ваш аккаунт на сайте &lt;site&gt;
 *       был успешно создан.» (название сайта подставляется из конфигурации);
 *   <li>USER_DELETED — «Здравствуйте! Ваш аккаунт был удалён.»
 * </ul>
 *
 * <p>Хранение текстов в enum, а не в теле сервисов, даёт единую точку
 * изменения формулировок и позволяет зафиксировать их unit-тестом.
 */
public enum NotificationType {
    
    USER_CREATED(
            "Регистрация на сайте",
            "Здравствуйте! Ваш аккаунт на сайте %s был успешно создан."
    ),
    USER_DELETED(
            "Удаление аккаунта",
            "Здравствуйте! Ваш аккаунт был удалён."
    );
    
    private final String subject;
    private final String messageTemplate;
    
    NotificationType(String subject, String messageTemplate) {
        this.subject = subject;
        this.messageTemplate = messageTemplate;
    }
    
    public String getSubject() {
        return subject;
    }
    
    /**
     * Подстановка названия сайта в шаблон тела письма.
     *
     * <p>Для шаблонов без плейсхолдера %s (USER_DELETED) переданный аргумент
     * игнорируется: String.formatted допускает лишние аргументы,
     * ошибка возникает только при НЕДОСТАТКЕ аргументов.
     * Это позволяет вызывать метод единообразно для всех типов.
     */
    public String renderMessage(String siteName) {
        return messageTemplate.formatted(siteName);
    }
}