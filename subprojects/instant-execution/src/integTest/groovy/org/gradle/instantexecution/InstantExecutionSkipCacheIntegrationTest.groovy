/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution


class InstantExecutionSkipCacheIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "skip reading cached state on --refresh-dependencies"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {

                @Input abstract Property<String> getMessage()

                @TaskAction def action() {
                    println(message.get())
                }
            }
            tasks.register("myTask", MyTask) {
                // use an undeclared input so we can test --refresh-dependencies
                message.set(new File("message").text)
            }
        """
        file("message") << "foo"

        when:
        instantRun "myTask"

        then:
        outputContains("foo")
        instantExecution.assertStateStored()

        when:
        instantRun "myTask"

        then:
        outputContains("foo")
        instantExecution.assertStateLoaded()

        when:
        file("message").text = "bar"

        and:
        instantRun "myTask"

        then:
        outputContains("foo")
        instantExecution.assertStateLoaded()

        when:
        instantRun "myTask", "--refresh-dependencies"

        then:
        outputContains("bar")
        outputContains("Calculating task graph as instant execution cache cannot be reused due to --refresh-dependencies")
        instantExecution.assertStateStored()

        when:
        instantRun "myTask"

        then:
        outputContains("bar")
        instantExecution.assertStateLoaded()
    }
}
