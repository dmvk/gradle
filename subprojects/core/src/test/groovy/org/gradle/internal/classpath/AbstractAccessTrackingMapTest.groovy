/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classpath

import com.google.common.collect.Maps
import spock.lang.Specification

import java.util.function.BiConsumer

abstract class AbstractAccessTrackingMapTest extends Specification {
    protected final Map<String, String> innerMap = ['existing': 'existingValue', 'other': 'otherValue']
    protected final BiConsumer<Object, Object> consumer = Mock()

    protected abstract Map<? super String, ? super String> getMapUnderTestToRead()

    def "get(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().get(key)

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | null            | null
    }

    def "getOrDefault(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().getOrDefault(key, 'defaultValue')

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | 'defaultValue'  | null
    }


    def "containsKey(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().containsKey(key)

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult | reportedValue
        'existing' | true           | 'existingValue'
        'missing'  | false          | null
    }

    def "access to missing element with containsKey is tracked"() {
        when:
        def result = getMapUnderTestToRead().containsKey('missing')

        then:
        !result
        1 * consumer.accept('missing', null)
        0 * consumer._
    }

    def "access to existing element with forEach() is tracked"() {
        when:
        def iterated = new HashMap<Object, Object>()
        getMapUnderTestToRead().forEach { k, v -> iterated.put(k, v) }

        then:
        iterated == innerMap
        1 * consumer.accept('existing', 'existingValue')
        1 * consumer.accept('other', 'otherValue')
        0 * consumer._
    }

    def "entrySet() enumeration is tracked"() {
        when:
        def result = new HashSet<>(getMapUnderTestToRead().entrySet())

        then:
        result == innerMap.entrySet()
        1 * consumer.accept('existing', 'existingValue')
        1 * consumer.accept('other', 'otherValue')
        0 * consumer._
    }

    def "entrySet() contains(entry(#key, #requestedValue)) and containsAll(entry(#key, #requestedValue)) are tracked"() {
        when:
        def containsResult = getMapUnderTestToRead().entrySet().contains(entry(key, requestedValue))

        then:
        containsResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().entrySet().containsAll(Collections.singleton(entry(key, requestedValue)))

        then:
        containsAllResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | requestedValue  | reportedValue   | expectedResult
        'existing' | 'existingValue' | 'existingValue' | true
        'existing' | 'otherValue'    | 'existingValue' | false
        'missing'  | 'someValue'     | null            | false
    }

    def "entrySet() containsAll(entry(#key1, #requestedValue1), entry(#key2, #requestedValue2)) are tracked"() {
        when:
        def result = getMapUnderTestToRead().entrySet().containsAll(
            Arrays.asList(entry(key1, requestedValue1), entry(key2, requestedValue2)))

        then:
        result == expectedResult
        1 * consumer.accept(key1, reportedValue1)
        1 * consumer.accept(key2, reportedValue2)
        0 * consumer._

        where:
        key1       | requestedValue1    | reportedValue1  | key2          | requestedValue2    | reportedValue2 | expectedResult
        'existing' | 'existingValue'    | 'existingValue' | 'other'       | 'otherValue'       | 'otherValue'   | true
        'existing' | 'nonexistingValue' | 'existingValue' | 'other'       | 'otherValue'       | 'otherValue'   | false
        'existing' | 'nonexistingValue' | 'existingValue' | 'missing'     | 'missingValue'     | null           | false
        'missing'  | 'missingValue'     | null            | 'alsoMissing' | 'nonexistingValue' | null           | false
    }

    def "keySet() contains(#key) and containsAll(#key) is tracked"() {
        when:
        def containsResult = getMapUnderTestToRead().keySet().contains(key)

        then:
        containsResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().keySet().containsAll(Collections.singleton(key))

        then:
        containsAllResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult | reportedValue
        'existing' | true           | 'existingValue'
        'missing'  | false          | null
    }

    def "keySet() containsAll(#key1, #key2) is tracked"() {
        when:
        def result = getMapUnderTestToRead().keySet().containsAll(Arrays.asList(key1, key2))

        then:
        result == expectedResult
        1 * consumer.accept(key1, reportedValue1)
        1 * consumer.accept(key2, reportedValue2)
        0 * consumer._

        where:
        key1       | reportedValue1  | key2           | reportedValue2 | expectedResult
        'existing' | 'existingValue' | 'other'        | 'otherValue'   | true
        'existing' | 'existingValue' | 'missing'      | null           | false
        'missing'  | null            | 'otherMissing' | null           | false
    }

    static Map.Entry<String, String> entry(String key, String value) {
        return Maps.immutableEntry(key, value)
    }
}
