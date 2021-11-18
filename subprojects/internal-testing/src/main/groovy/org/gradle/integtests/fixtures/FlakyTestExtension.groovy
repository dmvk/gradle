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
package org.gradle.integtests.fixtures

import org.gradle.util.FlakyTest
import org.opentest4j.TestAbortedException
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

import java.lang.reflect.Method

class FlakyTestExtension implements IGlobalExtension {
    private final boolean inFlakyTestQuarantine = Boolean.getBoolean("org.gradle.flakyTestQuarantine")
    private final interceptor = new IMethodInterceptor() {
        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            FlakyTest flakyTestAnnotation = findAnnotation(invocation.feature.featureMethod.reflection)
            String issue = flakyTestAnnotation?.issue()
            boolean isFlaky = flakyTestAnnotation != null && flakyTestAnnotation.flakyIf().newInstance(null, null).call()
            if (inFlakyTestQuarantine) {
                if (isFlaky) {
                    invocation.proceed()
                } else {
                    throw new TestAbortedException(issue ?: "Skip because of flakiness")
                }
            } else {
                if (!isFlaky) {
                    invocation.proceed()
                } else {
                    throw new TestAbortedException(issue ?: "Skip because of flakiness")
                }
            }
        }
    }

    private FlakyTest findAnnotation(Method method) {
        FlakyTest annotationOnClass = method.getDeclaringClass().getAnnotation(FlakyTest)
        if (annotationOnClass) {
            return annotationOnClass
        }
        return method.getAnnotation(FlakyTest)
    }

    @Override
    void visitSpec(SpecInfo spec) {
        spec.features.each {
            it.addInterceptor(interceptor)
        }
    }
}
