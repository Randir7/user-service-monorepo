package com.github.randir7.userservice.mapper;

import com.github.randir7.userservice.dto.UserDto;
import com.github.randir7.userservice.dto.UserDtoResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Маппер для преобразования UserDto в UserDtoResource.
 *
 * <p>Разделяет два слоя представления:
 * <ul>
 *   <li>UserDto — чистая передача данных (используется в сервисном слое)</li>
 *   <li>UserDtoResource — представление с гипермедиа-ссылками (используется в контроллере)</li>
 * </ul>
 *
 * <p>Такой подход позволяет тестировать бизнес-логику без зависимостей
 * от HATEOAS и добавлять ссылки только в контроллере, где они нужны.
 */
@Component
public class UserDtoResourceMapper {
    
    /**
     * Преобразование UserDto в UserDtoResource.
     * Ссылки добавляются отдельно в контроллере.
     */
    public UserDtoResource toResource(UserDto dto) {
        return new UserDtoResource(
                dto.id(),
                dto.name(),
                dto.email(),
                dto.age(),
                dto.createdAt()
        );
    }
    
    /**
     * Преобразование списка UserDto в список UserDtoResource.
     */
    public List<UserDtoResource> toResourceList(List<UserDto> dtos) {
        return dtos.stream()
                .map(this::toResource)
                .toList();
    }
}