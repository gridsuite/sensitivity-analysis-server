/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

import java.util.Collection;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public final class SensitivityResultSpecification { // TODO : à virer ?
    public static final String CONTINGENCY = "contingencyResult";

    private SensitivityResultSpecification() {
        // Should not be instantiated
    }

    public static Specification<SensitivityResultEntity> nullContingency() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
            criteriaBuilder.isNull(root.get(CONTINGENCY))
        );
    }

    public static Specification<SensitivityResultEntity> fieldIn(Collection<?> collection,
                                                                  String fieldName,
                                                                  String subFieldName) {
        return (root, query, criteriaBuilder) -> {
            if (!CollectionUtils.isEmpty(collection)) {
                var field = subFieldName == null ? root.get(fieldName) : root.get(fieldName).get(subFieldName);
                return collection.stream()
                    .map(id -> criteriaBuilder.equal(field, id))
                    .reduce(criteriaBuilder.or(), criteriaBuilder::or);
            }
            return criteriaBuilder.and(); // Always True
        };
    }
}
