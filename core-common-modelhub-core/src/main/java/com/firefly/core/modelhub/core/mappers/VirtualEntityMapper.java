/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.firefly.core.modelhub.core.mappers;

import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.models.entities.VirtualEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for converting between VirtualEntity entity and VirtualEntityDto.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface VirtualEntityMapper {

    /**
     * Maps a VirtualEntity entity to a VirtualEntityDto.
     *
     * @param entity the entity to map
     * @return the mapped DTO
     */
    VirtualEntityDto toDto(VirtualEntity entity);

    /**
     * Maps a VirtualEntityDto to a VirtualEntity entity.
     *
     * @param dto the DTO to map
     * @return the mapped entity
     */
    VirtualEntity toEntity(VirtualEntityDto dto);
}