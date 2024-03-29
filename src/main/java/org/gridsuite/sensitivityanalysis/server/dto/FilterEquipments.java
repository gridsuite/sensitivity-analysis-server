package org.gridsuite.sensitivityanalysis.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterEquipments {
    @Schema(description = "filter id")
    private UUID filterId;

    @Schema(description = "equipments of filter")
    private List<IdentifiableAttributes> identifiableAttributes;

    @Schema(description = "equipments not found in network")
    private List<String> notFoundEquipments;
}
