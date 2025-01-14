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

package org.gradle.internal.resources;

import org.gradle.util.Path;

public class TaskExecutionLockRegistry extends AbstractResourceLockRegistry<Path, TaskExecutionLock> {
    private final ProjectLockRegistry projectLockRegistry;

    public TaskExecutionLockRegistry(ResourceLockCoordinationService coordinationService, ProjectLockRegistry projectLockRegistry) {
        super(coordinationService);
        this.projectLockRegistry = projectLockRegistry;
    }

    public ResourceLock getTaskExecutionLock(Path buildIdentityPath, Path projectIdentityPath) {
        if (projectLockRegistry.getAllowsParallelExecution()) {
            return projectLockRegistry.getResourceLock(buildIdentityPath, projectIdentityPath);
        } else {
            return getTaskExecutionLockForBuild(buildIdentityPath, projectIdentityPath);
        }
    }

    private ResourceLock getTaskExecutionLockForBuild(final Path buildIdentityPath, final Path projectIdentityPath) {
        return getOrRegisterResourceLock(buildIdentityPath, new ResourceLockProducer<Path, TaskExecutionLock>() {
            @Override
            public TaskExecutionLock create(Path projectPath, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
                return new TaskExecutionLock("task execution for build " + buildIdentityPath.getPath(), projectLockRegistry.getResourceLock(buildIdentityPath, projectIdentityPath), coordinationService, owner);
            }
        });
    }
}
