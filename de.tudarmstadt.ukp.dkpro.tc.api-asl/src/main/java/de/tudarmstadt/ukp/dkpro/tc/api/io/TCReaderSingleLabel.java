package de.tudarmstadt.ukp.dkpro.tc.api.io;

import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;

/**
 * Interface that should be implemented by readers for single label setups.
 * 
 * @author zesch
 *
 */
public interface TCReaderSingleLabel
{
    /**
     * Returns the text classification outcome for the current single-label instance
     * 
     * @param jcas
     * @return
     * @throws CollectionException
     */
    public String getTextClassificationOutcome(JCas jcas) throws CollectionException;
}