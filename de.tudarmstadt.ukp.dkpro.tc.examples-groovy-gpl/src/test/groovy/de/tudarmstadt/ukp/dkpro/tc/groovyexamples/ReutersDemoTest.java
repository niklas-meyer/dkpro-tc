package de.tudarmstadt.ukp.dkpro.tc.groovyexamples;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import de.tudarmstadt.ukp.dkpro.lab.task.ParameterSpace;

/**
 * This is not exactly a unit test (yet). It just ensures that the experiments run without throwing
 * any exception. Additional unit tests should test the inner workings of the experiments
 * 
 * @author Oliver Ferschke
 * 
 */
public class ReutersDemoTest
{
    ReutersDemo experiment;
    ParameterSpace pSpace;

    @Before
    public void setup()
        throws Exception
    {
        String path = "target/repository/" + getClass().getSimpleName() + "/"
                + name.getMethodName();
        System.setProperty("DKPRO_HOME", new File(path).getAbsolutePath());

        experiment = new ReutersDemo();
    }

    @Test
    public void testGroovyCrossValidation()
        throws Exception
    {
        experiment.runCrossValidation();
    }

    @Test
    public void testGroovyTrainTest()
        throws Exception
    {
        experiment.runTrainTest();
    }

    @Rule
    public TestName name = new TestName();

    @Before
    public void printSeparator()
    {
        System.out.println("\n=== " + name.getMethodName() + " =====================");
    }
}