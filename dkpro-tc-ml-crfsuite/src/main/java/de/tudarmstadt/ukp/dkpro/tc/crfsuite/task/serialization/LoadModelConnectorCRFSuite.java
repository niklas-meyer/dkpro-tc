/**
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package de.tudarmstadt.ukp.dkpro.tc.crfsuite.task.serialization;

import static de.tudarmstadt.ukp.dkpro.tc.core.Constants.MODEL_CLASSIFIER;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.tc.api.features.FeatureExtractorResource_ImplBase;
import de.tudarmstadt.ukp.dkpro.tc.api.features.FeatureStore;
import de.tudarmstadt.ukp.dkpro.tc.api.features.Instance;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationOutcome;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationSequence;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.ModelSerialization_ImplBase;
import de.tudarmstadt.ukp.dkpro.tc.core.util.SaveModelUtils;
import de.tudarmstadt.ukp.dkpro.tc.core.util.TaskUtils;
import de.tudarmstadt.ukp.dkpro.tc.crfsuite.task.CRFSuiteTestTask;
import de.tudarmstadt.ukp.dkpro.tc.crfsuite.writer.CRFSuiteDataWriter;
import de.tudarmstadt.ukp.dkpro.tc.ml.uima.TcAnnotator;

public class LoadModelConnectorCRFSuite
    extends ModelSerialization_ImplBase
{

    @ConfigurationParameter(name = TcAnnotator.PARAM_TC_MODEL_LOCATION, mandatory = true)
    private File tcModelLocation;

    @ExternalResource(key = PARAM_FEATURE_EXTRACTORS, mandatory = true)
    protected FeatureExtractorResource_ImplBase[] featureExtractors;

    @ConfigurationParameter(name = PARAM_FEATURE_STORE_CLASS, mandatory = true)
    private String featureStoreImpl;

    private File model = null;
    private String executablePath = null;
    private Path tmpFolderForFeatureFile = null;

    @Override
    public void initialize(UimaContext context)
        throws ResourceInitializationException
    {
        super.initialize(context);

        try {
            tmpFolderForFeatureFile = Files
                    .createTempDirectory("temp" + System.currentTimeMillis());
            executablePath = CRFSuiteTestTask.getExecutablePath();
            model = new File(tcModelLocation, MODEL_CLASSIFIER);
            SaveModelUtils.verifyTcVersion(tcModelLocation,getClass());
        }
        catch (Exception e) {
            throw new ResourceInitializationException(e);
        }

    }

    @Override
    public void process(JCas jcas)
        throws AnalysisEngineProcessException
    {
        try {
            FeatureStore featureStore = (FeatureStore) Class.forName(featureStoreImpl)
                    .newInstance();
            int sequenceId = 0;
            for (TextClassificationSequence seq : JCasUtil.select(jcas,
                    TextClassificationSequence.class)) {

                List<Instance> instances = TaskUtils.getInstancesInSequence(featureExtractors,
                        jcas, seq, true, sequenceId++);

                for (Instance instance : instances) {
                    featureStore.addInstance(instance);
                }

            }

            File featureFile = CRFSuiteDataWriter.getFeatureFilename(tmpFolderForFeatureFile
                    .toFile());
            CRFSuiteDataWriter.writeFeatureFile(featureStore, featureFile);

            String labels = classify(featureFile);
            setPredictedOutcome(jcas, labels);
        }
        catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }

    }

    private void setPredictedOutcome(JCas jcas, String aLabels)
    {
        List<TextClassificationOutcome> outcomes = new ArrayList<TextClassificationOutcome>(
                JCasUtil.select(jcas, TextClassificationOutcome.class));
        String[] labels = aLabels.split("\n");

        for (int i = 0, labelIdx = 0; i < outcomes.size(); i++) {
            if (labels[labelIdx].isEmpty()) {
                // empty lines mark end of sequence
                // shift label index +1 to begin of next sequence
                labelIdx++;
            }
            TextClassificationOutcome o = outcomes.get(i);
            o.setOutcome(labels[labelIdx++]);
        }

    }

    private String classify(File featureFile)
        throws Exception
    {
        List<String> commandGoldPredictionOutput = CRFSuiteTestTask.wrapTestCommandAsList(
                featureFile, executablePath, model.getAbsolutePath());

        // remove 'print gold label' parameter
        List<String> commandPredictionOutput = deleteGoldOutputFromParameterList(commandGoldPredictionOutput);

        return CRFSuiteTestTask.runTest(commandPredictionOutput);
    }

    private static List<String> deleteGoldOutputFromParameterList(
            List<String> aCommandGoldPredictionOutput)
    {
        List<String> command = new ArrayList<String>();
        for (String parameter : aCommandGoldPredictionOutput) {
            if (parameter.equals("-r")) {
                continue;
            }
            command.add(parameter);
        }

        return command;
    }
}