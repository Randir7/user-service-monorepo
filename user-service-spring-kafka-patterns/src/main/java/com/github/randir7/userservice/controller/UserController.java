package com.github.randir7.userservice.controller;

import com.github.randir7.userservice.dto.CreateUserRequest;
import com.github.randir7.userservice.dto.UpdateUserRequest;
import com.github.randir7.userservice.dto.UserDto;
import com.github.randir7.userservice.dto.UserDtoResource;
import com.github.randir7.userservice.exception.ErrorResponse;
import com.github.randir7.userservice.mapper.UserDtoResourceMapper;
import com.github.randir7.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * REST-контроллер: HTTP-адаптер приложения.
 *
 * <p><b>@RestController = @Controller + @ResponseBody.</b>
 *
 * <p>@ResponseBody говорит Spring: результат каждого метода НЕ нужно
 * искать как view/шаблон — его надо СЕРИАЛИЗОВАТЬ в тело ответа.
 * Сериализацией занимается Jackson (уже внутри spring-boot-starter-web):
 * record UserDto автоматически превратится в JSON.
 *
 * <p><b>@RequestMapping("/api/v1/users")</b> — базовый путь для всех
 * методов класса.
 *
 * <p><b>ПРАВИЛО ТОНКОГО КОНТРОЛЛЕРА:</b> здесь НЕТ бизнес-логики.
 * Контроллер только: (1) распаковывает HTTP-запрос в объекты,
 * (2) вызывает сервис, (3) упаковывает результат в HTTP-ответ.
 * Вся логика — в UserService. Это делает сервис тестируемым без HTTP.
 *
 * <p>Используются аннотации OpenAPI:
 * @Tag — группирует эндпоинты в Swagger UI
 * @Operation — описание метода
 * @ApiResponse — документация кодов ответа
 * @Schema — описание структуры объектов
 *
 *
 * <p><b>@Tag</b> связывает контроллер с тегом "Users", объявленным
 *  * в OpenApiConfig: описание тега берётся из конфигурации, а все методы
 *  * класса автоматически группируются под этим тегом в Swagger UI.
 *  * Имя тега здесь и в OpenApiConfig обязано совпадать строка-в-строку.
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    private final UserService userService;
    private final UserDtoResourceMapper resourceMapper;
    
    // Инъекция через конструктор
    public UserController(UserService userService, UserDtoResourceMapper resourceMapper) {
        this.userService = userService;
        this.resourceMapper = resourceMapper;
    }
    
    /**
     * POST /api/v1/users — создание пользователя.
     *
     * <p><b>@RequestBody</b>: Jackson читает тело запроса и строит
     * CreateUserRequest (поля JSON → компоненты record по именам).
     *
     * <p><b>@Valid</b>: включает Bean Validation ПЕРЕД входом в метод.
     * Если @NotBlank/@Email/@Min из DTO нарушены, метод вообще не вызовется —
     * Spring бросит MethodArgumentNotValidException и вернет 400 Bad Request.
     *
     * <p><b>ResponseEntity<UserDto></b> — обертка, позволяющая управлять
     * статусом, заголовками и телом ответа одновременно.
     *
     * <p>Коды ошибок (400, 409) описаны ВРУЧНУЮ через @ApiResponses.
     * Причина: эти ответы формирует GlobalExceptionHandler, а не метод
     * контроллера, и Springdoc не может вывести их из сигнатуры метода
     * автоматически. Всё, что возвращается из обработчиков исключений,
     * декларируется в документации явно.
     */
    
    @Operation(
            summary = "Создать нового пользователя",
            description = "Валидирует входные данные, нормализует их (trim имени, "
                    + "email в нижнем регистре), сохраняет пользователя и публикует "
                    + "событие user-created в Kafka после коммита транзакции."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDtoResource.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email уже занят другим пользователем",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<UserDtoResource> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Данные нового пользователя; id и createdAt не передаются",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateUserRequest.class)))
            @Valid @RequestBody CreateUserRequest request) {
        
        UserDto created = userService.create(request);
        UserDtoResource resource = resourceMapper.toResource(created);
        
        
        /*
        Типобезопасный способ создания ссылок на методы контроллера
        
        methodOn(UserController.class).getById(1L) — вызов метода на прокси-объекте
         (не выполняется реально, только для извлечения метаданных)
        linkTo(...) — создает Link объект с URL /api/v1/users/1
        withSelfRel() — помечает ссылку как "self" (стандартное отношение)
         */
        resource.add(linkTo(methodOn(UserController.class).getById(created.id())).withSelfRel());
        resource.add(linkTo(UserController.class).withRel("users"));
        
        // Собираем Location: http://localhost:8080/api/v1/users/{id}
        // ServletUriComponentsBuilder берет схему/хост/порт ИЗ ТЕКУЩЕГО запроса,
        // поэтому ничего не хардкодится: сменится порт — URL останется верным.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")                       // добавляет /{id} к текущему пути
                .buildAndExpand(created.id())        // подставляет значение id
                .toUri();
        
        // 201 Created + заголовок Location + тело с созданным ресурсом
        return ResponseEntity.created(location).body(resource);
    }
    
    /**
     * GET /api/v1/users/{id} — получить одного пользователя.
     *
     * <p><b>@PathVariable</b>: значение из фигурных скобок пути ("{id}")
     * привязывается к параметру метода. Spring сам сконвертирует строку
     * "1" в Long. Если придут буквы ("abc") — будет 400 Bad Request
     * (MethodArgumentTypeMismatchException), метод не вызовется.
     */
    @Operation(
            summary = "Получить пользователя по идентификатору",
            description = "Возвращает пользователя с указанным id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь найден",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDtoResource.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным id не найден",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDtoResource> getById(
            @Parameter(description = "Идентификатор пользователя", example = "1", required = true)
            @PathVariable Long id) {
        
        UserDto user = userService.getById(id);
        UserDtoResource resource = resourceMapper.toResource(user);
        
        resource.add(linkTo(methodOn(UserController.class).getById(id)).withSelfRel());
        resource.add(linkTo(UserController.class).withRel("users"));
        resource.add(linkTo(methodOn(UserController.class).delete(id)).withRel("delete"));
        
        return ResponseEntity.ok(resource);
    }
    
    /**
     * GET /api/v1/users — список всех пользователей.
     *<p>Для ответа-коллекции используется arraySchema: она сообщает
     * спецификации, что тело — массив объектов UserDto, а не одиночный объект.
     * Без arraySchema Swagger UI показал бы схему элемента без признака массива.
     */
    @Operation(
            summary = "Получить список всех пользователей",
            description = "Возвращает всех пользователей, отсортированных по возрастанию id."
    )
    @ApiResponse(responseCode = "200", description = "Список пользователей; пустой массив, если данных нет",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = UserDtoResource.class))))
    @GetMapping
    public ResponseEntity<CollectionModel<UserDtoResource>> findAll() {
        
        var users = userService.findAll();
        var resources = resourceMapper.toResourceList(users);
        
        // Добавление ссылки к каждому ресурсу
        for (UserDtoResource resource : resources) {
            resource.add(linkTo(methodOn(UserController.class).getById(resource.getId())).withSelfRel());
        }
        
        // CollectionModel - это обертка для коллекций ресурсов.
        // Позволяет добавить ссылки к самой коллекции (например, ссылку "self" на /api/v1/users).
        CollectionModel<UserDtoResource> collection = CollectionModel.of(resources);
        collection.add(linkTo(UserController.class).withSelfRel());
        
        return ResponseEntity.ok(collection);
    }
    
    /**
     * PUT /api/v1/users/{id} — полное обновление.
     *
     * <p>Возвращается код 200 + обновленное тело: клиенту удобно сразу увидеть
     * итоговое состояние
     */
    @Operation(
            summary = "Полностью обновить пользователя",
            description = "Семантика PUT: ресурс заменяется целиком, все поля обязательны. "
                    + "Email, занятый другим пользователем, отклоняется с кодом 409."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь обновлён",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDtoResource.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным id не найден",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email уже занят другим пользователем",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDtoResource> update(
            @Parameter(description = "Идентификатор обновляемого пользователя", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Новые данные пользователя; заменяют текущие значения целиком",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UpdateUserRequest.class)))
            @Valid @RequestBody UpdateUserRequest request) {
        
        UserDto updated = userService.update(id, request);
        UserDtoResource resource = resourceMapper.toResource(updated);
        
        resource.add(linkTo(methodOn(UserController.class).getById(id)).withSelfRel());
        resource.add(linkTo(UserController.class).withRel("users"));
        
        return ResponseEntity.ok(resource);
    }
    
    /**
     * DELETE /api/v1/users/{id} — удаление.
     *
     * <p>204 No Content: операция успешна, но возвращать нечего.
     * ResponseEntity<Void> + .noContent() выражают это на уровне типов.
     *
     * <p>Для кода 204 секция content не указывается: у ответа нет тела,
     * и схеме в спецификации брать неоткуда.
     */
    @Operation(
            summary = "Удалить пользователя",
            description = "Удаляет пользователя и публикует событие user-deleted "
                    + "в Kafka после коммита транзакции."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь удалён"),
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным id не найден",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Идентификатор удаляемого пользователя", example = "1", required = true)
            @PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}