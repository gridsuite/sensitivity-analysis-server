package org.gridsuite.sensitivityanalysis.server.dto;

import com.powsybl.contingency.Contingency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/*
    @author Jamal KHEYYAD <jamal.kheyyad at rte-international.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class Contengencies {
    List<Contingency> contingenciesFound;
    List<UUID> contingenciesNotFound;
}
