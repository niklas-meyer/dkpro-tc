/*******************************************************************************
 * Copyright 2015
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
package de.tudarmstadt.ukp.dkpro.tc.core.feature;

import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationUnit;

/**
 * A static utility class for common methods and constants
 * required by the context meta collection process.
 * 
 */
public class ContextMetaCollectorUtil {
	
	// Public constants used in the context file format
    public static final char ID_CONTEXT_DELIMITER = (char) 9; 	// ASCII code for Tab character (appending "\t" to SB didn't work)
    public static final String RIGHT_CONTEXT_SEPARATOR = "]]" + ID_CONTEXT_DELIMITER;
    public static final String LEFT_CONTEXT_SEPARATOR = ID_CONTEXT_DELIMITER + "[[";
    
    // Width of the context before and after a unit (in characters)
    public static final int CONTEXT_WIDTH = 30;

	protected static void addContext(JCas jcas, TextClassificationUnit unit, String id, StringBuilder sb) {
        sb.append(id);
        sb.append(ID_CONTEXT_DELIMITER);
        sb.append(getLeftContext(jcas, unit));
        sb.append(LEFT_CONTEXT_SEPARATOR);
        String unitText = unit.getCoveredText();
        unitText = unitText.replace("\n", " ");		// removing line breaks, to avoid problems with CSV-style output format
    	sb.append(unitText);
    	sb.append(RIGHT_CONTEXT_SEPARATOR);
    	sb.append(getRightContext(jcas, unit));
        sb.append("\n");
	}
	
	private static String getLeftContext(JCas jcas, TextClassificationUnit unit) {
		int leftOffset = unit.getBegin() - CONTEXT_WIDTH;
		
		if (leftOffset < 0) {
			leftOffset = 0;
		}
		
		String context = jcas.getDocumentText().substring(leftOffset, unit.getBegin());
		context = context.replaceAll("\n", " ");
		
		return context;
	}
	
	private static String getRightContext(JCas jcas, TextClassificationUnit unit) {
		int rightOffset = unit.getEnd() + CONTEXT_WIDTH;
		
		if (rightOffset > jcas.getDocumentText().length()) {
			rightOffset = jcas.getDocumentText().length();
		}
		
		String context = jcas.getDocumentText().substring(unit.getEnd(), rightOffset);
		context = context.replaceAll("\n", " ");
		
		return context;
	}
}
