/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import java.util.Comparator;
import java.util.Iterator;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * A matcher (for hamcrest assertThat) that checks an iterable is iterated in an order compatible with a given comparator.
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */
public class OrderMatcher<T, C extends Iterable<T>> extends TypeSafeMatcher<C> {

    private final Comparator<T> comparator;

    public static <TS, CO extends Iterable<TS>> OrderMatcher<TS, CO> isOrderedAccordingTo(Comparator<TS> comparator) {
        return new OrderMatcher<>(comparator);
    }

    public OrderMatcher(Comparator<T> comparator) {

        this.comparator = comparator;
    }

    protected boolean matchesSafely(C iterable) {
        Iterator<T> it = iterable.iterator();
        if (!it.hasNext()) {
            return true; // empty can not be found unsorted
        }

        T prev = it.next();
        while (it.hasNext()) {
            T curr = it.next();
            if (comparator.compare(prev, curr) > 0) {
                return false;
            }
            prev = curr;
        }

        return true;
    }

    @Override
    public void describeMismatchSafely(C iterable, Description mismatchDescription) {
        Iterator<T> it = iterable.iterator();
        if (!it.hasNext()) {
            return; // empty can not be found unsorted
        }

        T prev = it.next();
        while (it.hasNext()) {
            T curr = it.next();
            if (comparator.compare(prev, curr) > 0) {
                mismatchDescription.appendValue(prev).appendText(" and ").appendValue(curr)
                    .appendText(" are not ordered as given comparator expects");
            }
            prev = curr;
        }
    }

    public void describeTo(Description description) {
        description.appendText("Checks order of an iterable according to a given comparator");
    }
}
