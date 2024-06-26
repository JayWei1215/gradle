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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.ProblemTransformer;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.problems.internal.emitters.BuildOperationBasedProblemEmitter;
import org.gradle.problems.internal.transformers.ProblemStreamLocationTransformer;

import java.util.List;

public class ProblemsBuildTreeServices implements ServiceRegistrationProvider {

    @Provides
    InternalProblems createProblemsService(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        List<ProblemTransformer> transformers
    ) {
        BuildOperationBasedProblemEmitter emitter = new BuildOperationBasedProblemEmitter(buildOperationProgressEventEmitter);
        return new DefaultProblems(emitter, transformers, CurrentBuildOperationRef.instance());
    }

    @Provides
    ProblemTransformer createPluginIdLocationTransformer(ProblemStream problemStream) {
        return new ProblemStreamLocationTransformer(problemStream);
    }
}
