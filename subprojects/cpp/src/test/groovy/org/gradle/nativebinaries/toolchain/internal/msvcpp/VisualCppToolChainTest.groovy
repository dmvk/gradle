/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.internal.msvcpp

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.internal.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final ExecActionFactory execActionFactory = Mock(ExecActionFactory)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
        findInPath("cl.exe") >> file('VisualStudio/VC/bin/cl.exe')
        findInPath("rc.exe") >> file("SDK/bin/rc.exe")
    }
    final toolChain = new VisualCppToolChain("visualCpp", operatingSystem, fileResolver, execActionFactory)

    def "uses .lib file for shared library at link time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryLinkFileName("test") == "test.lib"
    }

    def "uses .dll file for shared library at runtime time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryName("test") == "test.dll"
    }

    def "locates visual studio installation and windows SDK based on executables in path"() {
        when:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == "Visual Studio installation cannot be found"

        when:
        createFile('VisualStudio/VC/bin/cl.exe')

        and:
        def availability2 = new ToolChainAvailability()
        toolChain.checkAvailable(availability2);

        then:
        !availability2.available
        availability2.unavailableMessage == "Windows SDK cannot be found"

        when:
        createFile('SDK/bin/rc.exe')
        createFile('SDK/lib/kernel32.lib')

        and:
        def availability3 = new ToolChainAvailability()
        toolChain.checkAvailable(availability3);

        then:
        availability3.available
    }

    def "resolves install directory"() {
        when:
        toolChain.installDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.installDir == file("one")
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
