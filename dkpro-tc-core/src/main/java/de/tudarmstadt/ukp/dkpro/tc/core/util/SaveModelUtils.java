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
package de.tudarmstadt.ukp.dkpro.tc.core.util;

import static de.tudarmstadt.ukp.dkpro.tc.core.task.MetaInfoTask.META_KEY;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.internal.ResourceManagerFactory;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.Resource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;

import de.tudarmstadt.ukp.dkpro.lab.engine.TaskContext;
import de.tudarmstadt.ukp.dkpro.lab.storage.StorageService.AccessMode;
import de.tudarmstadt.ukp.dkpro.tc.api.features.meta.MetaCollector;
import de.tudarmstadt.ukp.dkpro.tc.core.Constants;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.ModelSerialization_ImplBase;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.TCMachineLearningAdapter;
import de.tudarmstadt.ukp.dkpro.tc.core.util.TaskUtils;

/**
 * Demo to show case how to train and save a model in document mode and multi-label classification
 * using Meka/Weka.
 */
public class SaveModelUtils
    implements Constants
{
    private static final String TCVERSION = "TcVersion";

    public static void writeFeatureInformation(File outputFolder, List<String> featureSet)
        throws Exception
    {
        String featureExtractorString = StringUtils.join(featureSet, "\n");
        FileUtils.writeStringToFile(new File(outputFolder, MODEL_FEATURE_EXTRACTORS),
                featureExtractorString);
    }

    public static void writeModelParameters(TaskContext aContext, File aOutputFolder,
            List<String> aFeatureSet, List<Object> aFeatureParameters)
        throws Exception
    {
        // write meta collector data
        // automatically determine the required metaCollector classes from the
        // provided feature
        // extractors
        Set<Class<? extends MetaCollector>> metaCollectorClasses;
        try {
            metaCollectorClasses = TaskUtils.getMetaCollectorsFromFeatureExtractors(aFeatureSet);
        }
        catch (ClassNotFoundException e) {
            throw new ResourceInitializationException(e);
        }
        catch (InstantiationException e) {
            throw new ResourceInitializationException(e);
        }
        catch (IllegalAccessException e) {
            throw new ResourceInitializationException(e);
        }

        // collect parameter/key pairs that need to be set
        Map<String, String> metaParameterKeyPairs = new HashMap<String, String>();
        for (Class<? extends MetaCollector> metaCollectorClass : metaCollectorClasses) {
            try {
                metaParameterKeyPairs.putAll(metaCollectorClass.newInstance()
                        .getParameterKeyPairs());
            }
            catch (InstantiationException e) {
                throw new ResourceInitializationException(e);
            }
            catch (IllegalAccessException e) {
                throw new ResourceInitializationException(e);
            }
        }

        Properties parameterProperties = new Properties();
        for (Entry<String, String> entry : metaParameterKeyPairs.entrySet()) {
            File file = new File(aContext.getFolder(META_KEY, AccessMode.READWRITE),
                    entry.getValue());

            String name = file.getName();
            String subFolder = aOutputFolder.getAbsoluteFile() + "/" + name;
            File targetFolder = new File(subFolder);
            copyToTargetLocation(file, targetFolder);
            parameterProperties.put(entry.getKey(), name);

            // should never be reached
        }

        for (int i = 0; i < aFeatureParameters.size(); i = i + 2) {

            String key = (String) aFeatureParameters.get(i).toString();
            String value = aFeatureParameters.get(i + 1).toString();

            if (valueExistAsFileOrFolderInTheFileSystem(value)) {
                String name = new File(value).getName();
                String destination = aOutputFolder + "/" + name;
                copyToTargetLocation(new File(value), new File(destination));
                parameterProperties.put(key, name);
                continue;
            }
            parameterProperties.put(key, value);
        }

        FileWriter writer = new FileWriter(new File(aOutputFolder, MODEL_PARAMETERS));
        parameterProperties.store(writer, "");
        writer.close();
    }

    private static boolean valueExistAsFileOrFolderInTheFileSystem(String aValue)
    {
        return new File(aValue).exists();
    }

    private static void copyToTargetLocation(File source, File destination)
        throws IOException
    {

        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdir();
            }

            for (String file : source.list()) {
                File src = new File(source, file);
                File dest = new File(destination, file);
                copyToTargetLocation(src, dest);
            }

        }
        else {
            copySingleFile(source, destination);
        }
    }

    private static void copySingleFile(File source, File destination)
        throws IOException
    {
        InputStream inputstream = new FileInputStream(source);
        OutputStream outputstream = new FileOutputStream(destination);
        IOUtils.copy(inputstream, outputstream);
        inputstream.close();
        outputstream.close();
    }

    public static void writeModelAdapterInformation(File aOutputFolder, String aModelMeta)
        throws Exception
    {
        // as a marker for the type, write the name of the ml adapter class
        // write feature extractors
        FileUtils.writeStringToFile(new File(aOutputFolder, MODEL_META), aModelMeta);
    }

    public static void writeFeatureClassFiles(File modelFolder, List<String> featureSet)
        throws Exception
    {
        for (String featureString : featureSet) {
            Class<?> feature = Class.forName(featureString);
            InputStream inStream = feature.getResource(
                    "/" + featureString.replace(".", "/") + ".class").openStream();

            OutputStream outStream = buildOutputStream(modelFolder, featureString);

            IOUtils.copy(inStream, outStream);
            outStream.close();
            inStream.close();

        }

    }

    private static OutputStream buildOutputStream(File modelFolder, String featureString)
        throws Exception
    {

        String packagePath = featureString.substring(0, featureString.lastIndexOf(".")).replaceAll(
                "\\.", "/");
        String featureClassName = featureString.substring(featureString.lastIndexOf(".") + 1)
                + ".class";

        String folderPath = modelFolder.getAbsolutePath() + "/" + MODEL_FEATURE_CLASS_FOLDER + "/"
                + packagePath + "/";
        new File(folderPath).mkdirs();
        return new FileOutputStream(new File(folderPath + featureClassName));
    }

    public static void writeCurrentVersionOfDKProTC(File outputFolder)
        throws Exception
    {
        String version = getCurrentTcVersionFromJar();
        if (version == null) {
            version = getCurrentTcVersionFromWorkspace();
        }
        if (version != null) {
            Properties properties = new Properties();
            properties.setProperty(TCVERSION, version);

            File file = new File(outputFolder + "/" + MODEL_TC_VERSION);
            FileOutputStream fileOut = new FileOutputStream(file);
            properties.store(fileOut, "Version of DKPro TC used to train this model");
            fileOut.close();
        }

    }

    private static String getCurrentTcVersionFromWorkspace()
        throws Exception
    {
        Class<?> contextClass = SaveModelUtils.class;

        // Try to determine the location of the POM file belonging to the context object
        URL url = contextClass.getResource(contextClass.getSimpleName() + ".class");
        String classPart = contextClass.getName().replace(".", "/") + ".class";
        String base = url.toString();
        base = base.substring(0, base.length() - classPart.length());
        base = base.substring(0, base.length() - "target/classes/".length());
        File pomFile = new File(new File(URI.create(base)), "pom.xml");

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        model = reader.read(new FileInputStream(pomFile));
        String version = model.getParent().getVersion();

        return version;
    }

    public static void verifyTcVersion(File tcModelLocation,
            Class<? extends ModelSerialization_ImplBase> class1)
        throws Exception
    {
        String loadedVersion = SaveModelUtils.loadTcVersionFromModel(tcModelLocation);
        String currentVersion = SaveModelUtils.getCurrentTcVersionFromJar();

        if (currentVersion == null) {
            currentVersion = SaveModelUtils.getCurrentTcVersionFromWorkspace();
        }

        if (loadedVersion.equals(currentVersion)) {
            return;
        }
        Logger.getLogger(class1).warn(
                "The model was created under version [" + loadedVersion + "], you are using ["
                        + currentVersion + "]");
    }

    private static String getCurrentTcVersionFromJar()
    {
        Class<?> contextClass = SaveModelUtils.class;

        // String pomPattern = base + "META-INF/maven/" + modelGroup + "/" + moduleArtifactId +
        // "*/pom.xml";
        // PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // Resource[] resources = resolver.getResources(pomPattern);

        InputStream resourceAsStream = contextClass
                .getResourceAsStream("/META-INF/maven/de.tudarmstadt.ukp.dkpro.tc/dkpro-tc-core/pom.xml");

        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        try {
            model = reader.read(resourceAsStream);
        }
        catch (Exception e) {
            return null;
        }
        String version = model.getParent().getVersion();
        return version;
    }

    public static String loadTcVersionFromModel(File modelFolder)
        throws Exception
    {
        File file = new File(modelFolder, MODEL_TC_VERSION);
        Properties prop = new Properties();
        prop.load(new FileInputStream(file));
        return prop.getProperty(TCVERSION);
    }

    public static void writeFeatureMode(File outputFolder, String featureMode) throws IOException
    {
        Properties properties = new Properties();
        properties.setProperty(DIM_FEATURE_MODE, featureMode);

        File file = new File(outputFolder + "/" + MODEL_FEATURE_MODE);
        FileOutputStream fileOut = new FileOutputStream(file);
        properties.store(fileOut, "Feature mode used to train this model");
        fileOut.close();
        
    }

    public static void writeLearningMode(File outputFolder, String learningMode)  throws IOException
    {
        Properties properties = new Properties();
        properties.setProperty(DIM_LEARNING_MODE, learningMode);

        File file = new File(outputFolder + "/" + MODEL_LEARNING_MODE);
        FileOutputStream fileOut = new FileOutputStream(file);
        properties.store(fileOut, "Learning mode used to train this model");
        fileOut.close();        
    }

    public static String initFeatureMode(File tcModelLocation) throws IOException
    {
        File file = new File(tcModelLocation, MODEL_FEATURE_MODE);
        Properties prop = new Properties();
        prop.load(new FileInputStream(file));
        return prop.getProperty(DIM_FEATURE_MODE);
    }

    public static String initLearningMode(File tcModelLocation) throws IOException
    {
        File file = new File(tcModelLocation, MODEL_LEARNING_MODE);
        Properties prop = new Properties();
        prop.load(new FileInputStream(file));
        return prop.getProperty(DIM_LEARNING_MODE);
    }
    
    public static TCMachineLearningAdapter initMachineLearningAdapter(File tcModelLocation) throws Exception
    {
        File modelMeta = new File(tcModelLocation, MODEL_META);
        String fileContent = FileUtils.readFileToString(modelMeta);
        Class<?> classObj = Class.forName(fileContent);
        return (TCMachineLearningAdapter) classObj.newInstance();
    }

    public static List<String> initFeatureExtractors(File tcModelLocation)
        throws Exception
    {
        List<String> featureExtractors = new ArrayList<>();
        File featureExtractorsDescription = new File(tcModelLocation, MODEL_FEATURE_EXTRACTORS);
        List<String> featureConfiguration = FileUtils.readLines(featureExtractorsDescription);
        for (String featureExtractor : featureConfiguration) {
            featureExtractors.add(featureExtractor);
        }
        return featureExtractors;
    }

    public static List<Object> initParameters(File tcModelLocation)
        throws IOException
    {
        List<Object> parameters = new ArrayList<>();
        Properties parametersProp = new Properties();
        parametersProp.load(new FileReader(new File(tcModelLocation, MODEL_PARAMETERS)));
        for (Object key : parametersProp.keySet()) {
            parameters.add((String)key);
            if (isExistingFilePath(tcModelLocation, (String)parametersProp.get(key))) {
                parameters.add(tcModelLocation + "/" + (String)parametersProp.get(key));
            }
            else {
                parameters.add((String)parametersProp.get(key));
            }
        }
        return parameters;
    }

    private static boolean isExistingFilePath(File tcModelLocation, String name)
    {
        
        return new File(tcModelLocation.getAbsolutePath() + "/" + name).exists();
    }
    
    /**
     * Loads the java classes of the feature that are provided with the model and adds them to the classpath
     */
    public static List<ExternalResourceDescription> loadExternalResourceDescriptionOfFeatures(String outputPath,
            String[] featureExtractorClassNames, List<Object> convertedParameters)
                    throws ResourceInitializationException {

        List<ExternalResourceDescription> extractorResources = new ArrayList<ExternalResourceDescription>();
        try {
            File classFile = new File(outputPath + "/" + Constants.MODEL_FEATURE_CLASS_FOLDER);
            URLClassLoader urlClassLoader = new URLClassLoader(new URL[] { classFile.toURI().toURL() });

            for (String featureExtractor : featureExtractorClassNames) {

                Class<? extends Resource> resource = urlClassLoader.loadClass(featureExtractor)
                        .asSubclass(Resource.class);
                ExternalResourceDescription resourceDescription = createExternalResource(resource, convertedParameters);
                extractorResources.add(resourceDescription);

            }
            urlClassLoader.close();
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }
        return extractorResources;
    }

    static ExternalResourceDescription createExternalResource(Class<? extends Resource> resource,
            List<Object> convertedParameters) {
        return ExternalResourceFactory.createExternalResourceDescription(resource, convertedParameters.toArray());
    }

    /** 
     * Converts objects by calling <code>toString()</code> for each parameter 
     */
    public static List<Object> convertParameters(List<Object> parameters) {
        List<Object> convertedParameters = new ArrayList<Object>();
        if (parameters != null) {
            for (Object parameter : parameters) {
                convertedParameters.add(parameter.toString());
            }
        } else {
            parameters = new ArrayList<Object>();
        }
        return convertedParameters;
    }

    /**
     * Produces a resource manager that is used when creating the engine which is aware of the class files located in the model folder 
     */
    public static ResourceManager getModelFeatureAwareResourceManager(File tcModelLocation)
            throws ResourceInitializationException, MalformedURLException {
        // The features of a model are located in a subfolder where Java does
        // not look for them by default. This avoids that during model execution
        // several features with the same name are on the classpath which might
        // cause undefined behavior as it is not know which feature is first
        // found if several with same name exist. We create a new resource
        // manager here and point the manager explicitly to this subfolder where
        // the features to be used are located.
        ResourceManager resourceManager = ResourceManagerFactory.newResourceManager();
        String classpathOfModelFeatures = tcModelLocation.getAbsolutePath() + "/"
                + Constants.MODEL_FEATURE_CLASS_FOLDER;
        resourceManager.setExtensionClassPath(classpathOfModelFeatures, true);
        return resourceManager;
    }

	public static String initBipartitionThreshold(File tcModelLocation) throws FileNotFoundException, IOException {
        File file = new File(tcModelLocation, MODEL_BIPARTITION_THRESHOLD);
        Properties prop = new Properties();
        prop.load(new FileInputStream(file));
        return prop.getProperty(DIM_BIPARTITION_THRESHOLD);
	}

	public static void writeBipartitionThreshold(File outputFolder, String bipartitionThreshold) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(DIM_BIPARTITION_THRESHOLD, bipartitionThreshold);

        File file = new File(outputFolder + "/" + MODEL_BIPARTITION_THRESHOLD);
        FileOutputStream fileOut = new FileOutputStream(file);
        properties.store(fileOut, "Bipartition threshold used to train this model (only multi-label classification)");
        fileOut.close(); 	}
}
