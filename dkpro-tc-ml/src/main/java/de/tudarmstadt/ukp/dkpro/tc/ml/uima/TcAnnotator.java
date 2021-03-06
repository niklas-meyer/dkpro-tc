/*******************************************************************************
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.tc.ml.uima;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationFocus;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationOutcome;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationSequence;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationUnit;
import de.tudarmstadt.ukp.dkpro.tc.core.Constants;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.ModelSerialization_ImplBase;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.TCMachineLearningAdapter;
import de.tudarmstadt.ukp.dkpro.tc.core.util.SaveModelUtils;
import de.tudarmstadt.ukp.dkpro.tc.fstore.simple.DenseFeatureStore;

public class TcAnnotator
    extends JCasAnnotator_ImplBase
{

    public static final String PARAM_TC_MODEL_LOCATION = "tcModel";
    @ConfigurationParameter(name = PARAM_TC_MODEL_LOCATION, mandatory = true)
    protected File tcModelLocation;

    public static final String PARAM_NAME_SEQUENCE_ANNOTATION = "sequenceAnnotation";
    @ConfigurationParameter(name = PARAM_NAME_SEQUENCE_ANNOTATION, mandatory = false)
    private String nameSequence;

    public static final String PARAM_NAME_UNIT_ANNOTATION = "unitAnnotation";
    @ConfigurationParameter(name = PARAM_NAME_UNIT_ANNOTATION, mandatory = false)
    private String nameUnit;

    private String learningMode;
    private String featureMode;
    private String bipartitionThreshold;

    // private List<FeatureExtractorResource_ImplBase> featureExtractors;
    private List<String> featureExtractors;
    private List<Object> parameters;

    private TCMachineLearningAdapter mlAdapter;

    private AnalysisEngine engine;

    @Override
    public void initialize(UimaContext context)
        throws ResourceInitializationException
    {
        super.initialize(context);

        try {
            mlAdapter = SaveModelUtils.initMachineLearningAdapter(tcModelLocation);
            parameters = SaveModelUtils.initParameters(tcModelLocation);
            featureExtractors = SaveModelUtils.initFeatureExtractors(tcModelLocation);
            featureMode = SaveModelUtils.initFeatureMode(tcModelLocation);
            learningMode = SaveModelUtils.initLearningMode(tcModelLocation);
            bipartitionThreshold = SaveModelUtils.initBipartitionThreshold(tcModelLocation);
            

            validateUimaParameter();

            AnalysisEngineDescription connector = getSaveModelConnector(parameters,
                    tcModelLocation.getAbsolutePath(), mlAdapter.getDataWriterClass().toString(),
                    learningMode, featureMode, bipartitionThreshold, DenseFeatureStore.class.getName(),
                    featureExtractors.toArray(new String[0]));

            engine = UIMAFramework.produceAnalysisEngine(connector,
                    SaveModelUtils.getModelFeatureAwareResourceManager(tcModelLocation), null);

        }
        catch (Exception e) {
            throw new ResourceInitializationException(e);
        }
    }

    private void validateUimaParameter()
    {
        switch (featureMode) {

        case Constants.FM_UNIT: {
            boolean unitAnno = nameUnit != null && !nameUnit.isEmpty();

            if (unitAnno) {
                return;
            }
            throw new IllegalArgumentException(
                    "Learning mode ["
                            + Constants.FM_UNIT
                            + "] requires an annotation name for [unit] (e.g. Token)");
        }

        case Constants.FM_SEQUENCE: {
            boolean seqAnno = nameSequence != null && !nameSequence.isEmpty();
            boolean unitAnno = nameUnit != null && !nameUnit.isEmpty();

            if (seqAnno && unitAnno) {
                return;
            }
            throw new IllegalArgumentException(
                    "Learning mode ["
                            + Constants.FM_SEQUENCE
                            + "] requires an annotation name for [sequence] (e.g. Sentence) and [unit] (e.g. Token)");
        }
        }
    }

    /**
     * @param featureExtractorClassNames
     * @return A fully configured feature extractor connector
     * @throws ResourceInitializationException
     */
    private AnalysisEngineDescription getSaveModelConnector(List<Object> parameters,
            String outputPath, String dataWriter, String learningMode, String featureMode,
            String bipartitionThreshold, String featureStore, String... featureExtractorClassNames)
        throws ResourceInitializationException
    {
        // convert parameters to string as external resources only take string parameters
        List<Object> convertedParameters = SaveModelUtils.convertParameters(parameters);

        List<ExternalResourceDescription> extractorResources = SaveModelUtils
                .loadExternalResourceDescriptionOfFeatures(outputPath, featureExtractorClassNames,
                        convertedParameters);

        // add the rest of the necessary parameters with the correct types
        parameters.addAll(Arrays.asList(PARAM_TC_MODEL_LOCATION,
                tcModelLocation, ModelSerialization_ImplBase.PARAM_OUTPUT_DIRECTORY, outputPath,
                ModelSerialization_ImplBase.PARAM_DATA_WRITER_CLASS, dataWriter,
                ModelSerialization_ImplBase.PARAM_LEARNING_MODE, learningMode,
                ModelSerialization_ImplBase.PARAM_BIPARTITION_THRESHOLD, bipartitionThreshold,
                ModelSerialization_ImplBase.PARAM_FEATURE_EXTRACTORS, extractorResources,
                ModelSerialization_ImplBase.PARAM_FEATURE_FILTERS, null,
                ModelSerialization_ImplBase.PARAM_IS_TESTING, true,
                ModelSerialization_ImplBase.PARAM_FEATURE_MODE, featureMode,
                ModelSerialization_ImplBase.PARAM_FEATURE_STORE_CLASS, featureStore));

        return AnalysisEngineFactory.createEngineDescription(
                mlAdapter.getLoadModelConnectorClass(), parameters.toArray());
    }

    @Override
    public void process(JCas jcas)
        throws AnalysisEngineProcessException
    {
        switch (featureMode) {
        case Constants.FM_DOCUMENT:
            processDocument(jcas);
            break;
        case Constants.FM_PAIR:
            // same as document
            processDocument(jcas);
            break;
        case Constants.FM_SEQUENCE:
            processSequence(jcas);
            break;
        case Constants.FM_UNIT:
            processUnit(jcas);
            break;
        }
    }

    private void processUnit(JCas jcas)
        throws AnalysisEngineProcessException
    {
        Type type = jcas.getCas().getTypeSystem().getType(nameUnit);
        Collection<AnnotationFS> select = CasUtil.select(jcas.getCas(), type);
        List<AnnotationFS> unitAnnotation = new ArrayList<AnnotationFS>(select);
        TextClassificationFocus tcf = null;
        TextClassificationOutcome tco = null;
        List<String> outcomes = new ArrayList<String>();

        // iterate the units and set on each the focus with a prepared dummy
        // outcome
        for (AnnotationFS unit : unitAnnotation) {
            TextClassificationUnit tcs = new TextClassificationUnit(jcas, unit.getBegin(),
                    unit.getEnd());
            tcs.addToIndexes();

            tcf = new TextClassificationFocus(jcas, unit.getBegin(), unit.getEnd());
            tcf.addToIndexes();

            tco = new TextClassificationOutcome(jcas, unit.getBegin(), unit.getEnd());
            tco.setOutcome("dummyValue");
            tco.addToIndexes();

            engine.process(jcas);

            // store the outcome
            outcomes.add(tco.getOutcome());

            tcf.removeFromIndexes();
            tco.removeFromIndexes();
        }

        // iterate again to set for each unit the outcome
        for (int i = 0; i < unitAnnotation.size(); i++) {
            AnnotationFS unit = unitAnnotation.get(i);
            tco = new TextClassificationOutcome(jcas, unit.getBegin(), unit.getEnd());
            tco.setOutcome(outcomes.get(i));
            tco.addToIndexes();
        }

    }

    private void processSequence(JCas jcas)
        throws AnalysisEngineProcessException
    {
        Logger.getLogger(getClass()).debug("START: process(JCAS)");

        addTCSequenceAnnotation(jcas);
        addTCUnitAndOutcomeAnnotation(jcas);

        // process and classify
        engine.process(jcas);

        // for (TextClassificationOutcome o : JCasUtil.select(jcas,
        // TextClassificationOutcome.class)){
        // System.out.println(o.getOutcome());
        // }

        Logger.getLogger(getClass()).debug("FINISH: process(JCAS)");
    }

    private void addTCUnitAndOutcomeAnnotation(JCas jcas)
    {
        Type type = jcas.getCas().getTypeSystem().getType(nameUnit);

        Collection<AnnotationFS> unitAnnotation = CasUtil.select(jcas.getCas(), type);
        for (AnnotationFS unit : unitAnnotation) {
            TextClassificationUnit tcs = new TextClassificationUnit(jcas, unit.getBegin(),
                    unit.getEnd());
            tcs.addToIndexes();
            TextClassificationOutcome tco = new TextClassificationOutcome(jcas, unit.getBegin(),
                    unit.getEnd());
            tco.setOutcome("dummyValue");
            tco.addToIndexes();
        }
    }

    private void addTCSequenceAnnotation(JCas jcas)
    {
        Type type = jcas.getCas().getTypeSystem().getType(nameSequence);

        Collection<AnnotationFS> sequenceAnnotation = CasUtil.select(jcas.getCas(), type);
        for (AnnotationFS seq : sequenceAnnotation) {
            TextClassificationSequence tcs = new TextClassificationSequence(jcas, seq.getBegin(),
                    seq.getEnd());
            tcs.addToIndexes();
        }
    }

    private void processDocument(JCas jcas)
        throws AnalysisEngineProcessException
    {
        // we need an outcome annotation to be present
        TextClassificationOutcome outcome = new TextClassificationOutcome(jcas);
        outcome.setOutcome("");
        outcome.addToIndexes();

        // create new UIMA annotator in order to separate the parameter spaces
        // this annotator will get initialized with its own set of parameters loaded from the model
        try {
            engine.process(jcas);
        }
        catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

}
