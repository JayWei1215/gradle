/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier

class PlainBuildStatusRendererTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    long currentTimeMs
    def renderer = new PlainBuildStatusRenderer(listener)

    @Override
    UpdateNowEvent updateNow() {
        return new UpdateNowEvent(currentTimeMs)
    }

    def "forwards event list to listener"() {
        def event = event("message")

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
    }

    def "formats given message with an incrementing timer"() {
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: INITIALIZING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })
    }

    def "hides timer between build phases"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: INITIALIZING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(1, 'WAITING'))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: WAITING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })
    }

    def "formats build"() {
        given:
        def event1 = startRootBuildOperation(1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: INITIALIZING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: WAITING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })
    }

    def "formats configuration phase"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 4)
        def event3 = startConfigureProject(3, 2)
        def event4 = startConfigureProject(4, 2)
        def event5 = startConfigureProject(5, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 25%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event4)
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 75%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })
    }

    def "formats configuration phase with nested builds"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 4)
        def event3 = startConfigureBuild(3, 2, 6)
        def event4 = startConfigureProject(4, 3)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 10%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        3 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })
    }

    def "formats execution phase"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteTask(3, 2)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: EXECUTING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: EXECUTING 25%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })
    }

    def "formats execution phase with nested builds"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startExecuteRootBuild(2, 1, 4)
        def event3 = startExecuteBuild(3, 2, 6)
        def event4 = startExecuteTask(4, 3)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: EXECUTING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: EXECUTING 10%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        3 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })
    }

    def "configuration phase runs until task graph execution"() {
        given:
        def event1 = startRootBuildOperation(1)
        def event2 = startConfigureRootBuild(2, 1, 1)
        def event3 = startConfigureProject(3, 2)
        def event4 = startOther(4, 1)
        def event5 = startExecuteRootBuild(5, 1, 1)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event3)
        renderer.onOutput(complete(3))
        renderer.onOutput(complete(2))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: CONFIGURING 100%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(event4)
        renderer.onOutput(updateNow())

        then:
        2 * listener.onOutput(!{
            verifyAll(it, LogEvent) {
                category == "progress"
            }
        })

        when:
        renderer.onOutput(complete(4))
        renderer.onOutput(event5)
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: EXECUTING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })

        when:
        renderer.onOutput(complete(5))
        renderer.onOutput(complete(1))
        renderer.onOutput(updateNow())

        then:
        1 * listener.onOutput({
            verifyAll(it, LogEvent) {
                message == "> Progress: WAITING 0%"
                category == "progress"
                logLevel == LogLevel.LIFECYCLE
                timestamp == currentTimeMs
            }
        })
    }

    def startRootBuildOperation(Long id) {
        new ProgressStartEvent(new OperationIdentifier(id), null, currentTimeMs, "category", "description", null, null, 0, true, new OperationIdentifier(id), BuildOperationCategory.UNCATEGORIZED)
    }

    def startConfigureRootBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_ROOT_BUILD, totalProgress)
    }

    def startConfigureBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_BUILD, totalProgress)
    }

    def startConfigureProject(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.CONFIGURE_PROJECT)
    }

    def startExecuteRootBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_MAIN_TASKS, totalProgress)
    }

    def startExecuteBuild(Long id, Long parentId, int totalProgress) {
        return start(id, parentId, BuildOperationCategory.RUN_WORK, totalProgress)
    }

    def startExecuteTask(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.TASK)
    }

    def startOther(Long id, Long parentId) {
        return start(id, parentId, BuildOperationCategory.UNCATEGORIZED)
    }

    def start(Long id, Long parentId, BuildOperationCategory category, int totalProgress = 0) {
        new ProgressStartEvent(new OperationIdentifier(id), new OperationIdentifier(parentId), currentTimeMs, "category", "description", null, null, totalProgress, true, new OperationIdentifier(id), category)
    }
}
