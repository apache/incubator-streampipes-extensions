/*
 * Copyright 2018 FZI Forschungszentrum Informatik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License at
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
package org.streampipes.processors.transformation.jvm.processor.csvmetadata;

import static org.streampipes.processors.transformation.jvm.processor.csvmetadata.CsvMetadataEnrichmentUtils.getCsvParser;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.streampipes.commons.exceptions.SpRuntimeException;
import org.streampipes.container.api.ResolvesContainerProvidedOptions;
import org.streampipes.container.api.ResolvesContainerProvidedOutputStrategy;
import org.streampipes.model.graph.DataProcessorDescription;
import org.streampipes.model.graph.DataProcessorInvocation;
import org.streampipes.model.schema.EventProperty;
import org.streampipes.model.schema.EventSchema;
import org.streampipes.model.schema.PropertyScope;
import org.streampipes.model.staticproperty.Option;
import org.streampipes.sdk.builder.ProcessingElementBuilder;
import org.streampipes.sdk.builder.StreamRequirementsBuilder;
import org.streampipes.sdk.extractor.ProcessingElementParameterExtractor;
import org.streampipes.sdk.extractor.StaticPropertyExtractor;
import org.streampipes.sdk.helpers.EpRequirements;
import org.streampipes.sdk.helpers.Labels;
import org.streampipes.sdk.helpers.Locales;
import org.streampipes.sdk.helpers.OutputStrategies;
import org.streampipes.sdk.utils.Assets;
import org.streampipes.wrapper.standalone.ConfiguredEventProcessor;
import org.streampipes.wrapper.standalone.declarer.StandaloneEventProcessingDeclarer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CsvMetadataEnrichmentController
        extends StandaloneEventProcessingDeclarer<CsvMetadataEnrichmentParameters>
        implements ResolvesContainerProvidedOptions,
        ResolvesContainerProvidedOutputStrategy<DataProcessorInvocation, ProcessingElementParameterExtractor> {

  private static final String MAPPING_FIELD_KEY = "mapping-field";
  private static final String CSV_FILE_KEY = "csv-file";
  private static final String FIELDS_TO_APPEND_KEY = "fields-to-append";
  private static final String FIELD_TO_MATCH = "field-to-match";

  @Override
  public DataProcessorDescription declareModel() {
    return ProcessingElementBuilder.create("org.streampipes.processors.transformation.jvm"
            + ".csvmetadata")
            .withLocales(Locales.EN)
            .withAssets(Assets.DOCUMENTATION)
            .requiredStream(StreamRequirementsBuilder.create()
                    .requiredPropertyWithUnaryMapping(
                            EpRequirements.anyProperty(),
                            Labels.withId(MAPPING_FIELD_KEY),
                            PropertyScope.NONE)
                    .build())
            .requiredFile(Labels.withId(CSV_FILE_KEY))
            .requiredSingleValueSelectionFromContainer(Labels.withId(FIELD_TO_MATCH),
                    Arrays.asList(MAPPING_FIELD_KEY, CSV_FILE_KEY))
            .requiredMultiValueSelectionFromContainer(Labels.withId(FIELDS_TO_APPEND_KEY),
                    Arrays.asList(MAPPING_FIELD_KEY, CSV_FILE_KEY, FIELD_TO_MATCH))
            .outputStrategy(OutputStrategies.customTransformation())
            .build();
  }

  @Override
  public ConfiguredEventProcessor<CsvMetadataEnrichmentParameters> onInvocation(DataProcessorInvocation graph, ProcessingElementParameterExtractor extractor) {
    String mappingFieldSelector = extractor.mappingPropertyValue(MAPPING_FIELD_KEY);
    List<String> fieldsToAppend = extractor.selectedMultiValues(FIELDS_TO_APPEND_KEY, String.class);
    String lookupField = extractor.selectedSingleValue(FIELD_TO_MATCH, String.class);
    String fileContents = null;
    try {
      fileContents = extractor.fileContentsAsString(CSV_FILE_KEY);
    } catch (IOException e) {
      e.printStackTrace();
    }

    CsvMetadataEnrichmentParameters params = new CsvMetadataEnrichmentParameters(graph,
            mappingFieldSelector,
            fieldsToAppend,
            lookupField,
            fileContents);

    return new ConfiguredEventProcessor<>(params, CsvMetadataEnrichment::new);
  }

  @Override
  public List<Option> resolveOptions(String requestId, StaticPropertyExtractor parameterExtractor) {
    try {
      String fileContents = parameterExtractor.fileContentsAsString(CSV_FILE_KEY);
      if (requestId.equals(FIELDS_TO_APPEND_KEY)) {
        String matchColumn = parameterExtractor.selectedSingleValue(FIELD_TO_MATCH, String.class);
        return getOptionsFromColumnNames(fileContents, Collections.singletonList(matchColumn));
      } else {
       return getOptionsFromColumnNames(fileContents, Collections.emptyList());
      }
    } catch (IOException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  @Override
  public EventSchema resolveOutputStrategy(DataProcessorInvocation processingElement, ProcessingElementParameterExtractor parameterExtractor) throws SpRuntimeException {
    List<EventProperty> properties = processingElement
            .getInputStreams()
            .get(0)
            .getEventSchema()
            .getEventProperties();

    List<String> columnsToInclude = parameterExtractor.selectedMultiValues(FIELDS_TO_APPEND_KEY,
            String.class);
    try {
      String fileContents = parameterExtractor.fileContentsAsString(CSV_FILE_KEY);
      properties.addAll(getAppendProperties(fileContents, columnsToInclude));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return new EventSchema(properties);
  }

  private List<EventProperty> getAppendProperties(String fileContents,
                                                  List<String> columnsToInclude) throws IOException {
    CSVParser parser = getCsvParser(fileContents);
    List<EventProperty> propertiesToAppend = new ArrayList<>();
    List<CSVRecord> records = parser.getRecords();
    if (records.size() > 0) {
      CSVRecord firstRecord = records.get(0);
      for (String column : columnsToInclude) {
        propertiesToAppend.add(makeEventProperty(column, firstRecord));
      }
    }
    return propertiesToAppend;
  }

  private EventProperty makeEventProperty(String column, CSVRecord firstRecord) {
    return CsvMetadataEnrichmentUtils.getGuessedEventProperty(column, firstRecord);
  }

  private List<Option> getOptionsFromColumnNames(String fileContents,
                                                 List<String> columnsToIgnore) throws IOException {
    return getColumnNames(fileContents, columnsToIgnore)
            .stream()
            .map(Option::new)
            .collect(Collectors.toList());
  }

  private List<String> getColumnNames(String fileContents, List<String> columnsToIgnore) throws IOException {
    CSVParser parser = getCsvParser(fileContents);
    return parser
            .getHeaderMap()
            .keySet()
            .stream()
            .filter(key -> columnsToIgnore.stream().noneMatch(c -> c.equals(key)))
            .collect(Collectors.toList());
  }
}