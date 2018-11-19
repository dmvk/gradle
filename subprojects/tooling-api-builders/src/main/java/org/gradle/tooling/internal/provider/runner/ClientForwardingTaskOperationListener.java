/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.Sets;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTaskResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTaskDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTaskFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSuccessResult;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import java.util.Collections;
import java.util.Set;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskOperationListener implements BuildOperationListener {
    private final ProgressEventConsumer eventConsumer;
    private final BuildClientSubscriptions clientSubscriptions;
    private final BuildOperationListener delegate;
    private final OperationResultPostProcessor operationResultPostProcessor;

    // BuildOperationListener dispatch is not serialized
    private final Set<Object> skipEvents = Sets.newConcurrentHashSet();

    ClientForwardingTaskOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate, OperationResultPostProcessor operationResultPostProcessor) {
        this.eventConsumer = eventConsumer;
        this.clientSubscriptions = clientSubscriptions;
        this.delegate = delegate;
        this.operationResultPostProcessor = operationResultPostProcessor;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier parentId = buildOperation.getParentId();
        if (parentId != null && skipEvents.contains(parentId)) {
            skipEvents.add(buildOperation.getId());
            return;
        }

        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            if (clientSubscriptions.isRequested(OperationType.TASK)) {
                TaskInternal task = ((ExecuteTaskBuildOperationDetails) buildOperation.getDetails()).getTask();
                eventConsumer.started(new DefaultTaskStartedProgressEvent(startEvent.getStartTime(), toTaskDescriptor(buildOperation, task)));
            } else {
                // Discard this operation and all children
                skipEvents.add(buildOperation.getId());
            }
        } else {
            delegate.started(buildOperation, startEvent);
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (skipEvents.remove(buildOperation.getId())) {
            return;
        }

        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            TaskInternal task = ((ExecuteTaskBuildOperationDetails) buildOperation.getDetails()).getTask();
            AbstractTaskResult result = operationResultPostProcessor.process(toTaskResult(task, finishEvent), buildOperation.getId());
            eventConsumer.finished(new DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), toTaskDescriptor(buildOperation, task), result));
        } else {
            delegate.finished(buildOperation, finishEvent);
        }
    }

    private DefaultTaskDescriptor toTaskDescriptor(BuildOperationDescriptor buildOperation, TaskInternal task) {
        Object id = buildOperation.getId();
        String taskIdentityPath = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        String taskPath = task.getIdentityPath().toString();
        Object parentId = eventConsumer.findStartedParentId(buildOperation.getParentId());
        return new DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parentId);
    }

    private static AbstractTaskResult toTaskResult(TaskInternal task, OperationFinishEvent result) {
        TaskStateInternal state = task.getState();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();

        if (state.getUpToDate()) {
            return new DefaultTaskSuccessResult(startTime, endTime, true, state.isFromCache(), state.getSkipMessage());
        } else if (state.getSkipped()) {
            return new DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage());
        } else {
            Throwable failure = result.getFailure();
            if (failure == null) {
                return new DefaultTaskSuccessResult(startTime, endTime, false, state.isFromCache(), "SUCCESS");
            } else {
                return new DefaultTaskFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
            }
        }
    }

}
