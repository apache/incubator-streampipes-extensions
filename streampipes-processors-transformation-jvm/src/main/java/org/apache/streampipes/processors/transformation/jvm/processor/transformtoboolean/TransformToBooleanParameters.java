/*
 * Copyright 2019 FZI Forschungszentrum Informatik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.streampipes.processors.transformation.jvm.processor.transformtoboolean;

import org.apache.streampipes.model.graph.DataProcessorInvocation;
import org.apache.streampipes.wrapper.params.binding.EventProcessorBindingParams;

import java.util.List;

public class TransformToBooleanParameters extends EventProcessorBindingParams {
    private List<String> transformFields;

    public TransformToBooleanParameters(DataProcessorInvocation graph, List<String> transformFields) {
        super(graph);
        this.transformFields = transformFields;
    }

    public List<String> getTransformFields() {
        return transformFields;
    }
}