/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.consumer.loader;


import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.tooling.internal.consumer.Distribution
import spock.lang.Ignore
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 12/15/11
 */
public class SynchronizedToolingImplementationLoaderTest extends Specification {

    def factory = Mock(ProgressLoggerFactory)
    def distro = Mock(Distribution)
    def logger = Mock(ProgressLogger)

    def loader = new SynchronizedToolingImplementationLoader(Mock(ToolingImplementationLoader))

    def setup() {
        loader.lock = Mock(Lock)
    }

    def "reports progress when busy"() {
        when:
        loader.create(distro, factory)

        then: "stubs"
        1 * loader.lock.tryLock() >> false
        1 * factory.newOperation(_ as Class) >> logger

        then:
        1 * logger.setDescription(_ as String)
        then:
        1 * logger.started()
        then:
        1 * loader.lock.lock()
        then:
        1 * loader.delegate.create(distro, factory)
        then:
        1 * logger.completed()
        1 * loader.lock.unlock()
        0 * _
    }

    def "does not report progress when appropriate"() {
        when:
        loader.create(distro, factory)

        then:
        1 * loader.lock.tryLock() >> true
        then:
        1 * loader.delegate.create(distro, factory)
        then:
        1 * loader.lock.unlock()
        0 * _
    }

    def concurrent = new ConcurrentTestUtil()

    def "is thread safe"() {
        given:
        loader.lock = new ReentrantLock()
        factory.newOperation(_ as Class) >> logger

        when:
        5.times {
            concurrent.start { loader.create(distro, factory) }
        }

        then:
        concurrent.finished()
    }

    @Ignore
    //below demonstrates somewhat inconvenient interference between spock and ConcurrentTestUtil
    //if you remove @Ignore the test will pass only when used with @IgnoreRest
    //this test lives only to demonstrate the issue. If you remove this test no coverage is lost.
    def "safely delegates creation of distro"() {
        given:
        loader.lock = new ReentrantLock()
        factory.newOperation(_ as Class) >> logger

        when:
        2.times {
            concurrent.start { loader.create(distro, factory) }
        }
        then:
        concurrent.finished()
        2 * loader.delegate.create(distro, factory)
    }
}