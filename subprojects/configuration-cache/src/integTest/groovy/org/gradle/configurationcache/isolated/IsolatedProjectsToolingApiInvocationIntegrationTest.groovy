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

package org.gradle.configurationcache.isolated

import org.gradle.configurationcache.fixtures.ToolingApiBackedGradleExecuter
import org.gradle.configurationcache.fixtures.ToolingApiSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter

class IsolatedProjectsToolingApiInvocationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements ToolingApiSpec {
    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "does not (yet) cache tooling models"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel()

        then:
        model.message == "It works!"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works!"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
    }

    def "reports cross project access from build script when fetching tooling model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Build file 'build.gradle': Cannot access project ':a' from project ':'",
                "Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (2 problems)")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Build file 'build.gradle': Cannot access project ':a' from project ':'",
                "Build file 'build.gradle': Cannot access project ':b' from project ':'")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (2 problems)")
    }

    def "reports cross project access from model builder while fetching tooling model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc("""
            project.subprojects.each { it.extensions }
        """)
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Plugin class 'my.MyPlugin': Cannot access project ':a' from project ':'",
                "Plugin class 'my.MyPlugin': Cannot access project ':b' from project ':'")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (2 problems)")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Plugin class 'my.MyPlugin': Cannot access project ':a' from project ':'",
                "Plugin class 'my.MyPlugin': Cannot access project ':b' from project ':'")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (2 problems)")
    }

    def "reports configuration cache problems in build script when fetching tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
            gradle.buildFinished {
                println("build finished")
            }
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Build file 'build.gradle': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (1 problem).")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Build file 'build.gradle': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (1 problem).")
    }

    def "reports configuration cache problems in model builder while fetching tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc("""
            project.gradle.buildFinished {
                println("build finished")
            }
        """)
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (1 problem).")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works!"
        problems.assertResultHasProblems(result) {
            withUniqueProblems("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        result.assertHasPostBuildOutput("Configuration cache problems found (1 problem).")
    }

}
