/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * partial type from ElementAttributes (Directory-server)
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@AllArgsConstructor
@NoArgsConstructor
public class ElementAttributes {
    @Getter
    private UUID elementUuid;
    @Setter
    @Getter
    private String elementName;
}
