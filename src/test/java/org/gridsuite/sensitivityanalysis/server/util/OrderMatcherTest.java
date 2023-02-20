package org.gridsuite.sensitivityanalysis.server.util;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.gridsuite.sensitivityanalysis.server.util.OrderMatcher.isOrderedAccordingTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class OrderMatcherTest {

    @Test
    void with() {
        assertThat(List.of(), isOrderedAccordingTo(Comparator.<Double>naturalOrder()));
        assertThat(List.of(0.0), isOrderedAccordingTo(Comparator.<Double>naturalOrder()));
        assertThat(List.of(0.0, 1.0), isOrderedAccordingTo(Comparator.<Double>naturalOrder()));
        assertThat(List.of(0.0, 1.0, 2.0), isOrderedAccordingTo(Comparator.<Double>naturalOrder()));
        assertThat(List.of(0.0, 2.0, 1.0), not(isOrderedAccordingTo(Comparator.<Double>naturalOrder())));
        assertThat(List.of(2.0, 1.0), not(isOrderedAccordingTo(Comparator.<Double>naturalOrder())));
    }
}
