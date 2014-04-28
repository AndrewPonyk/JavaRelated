package xslt;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class XSLTTransform {
	final static String FILENAME = System.getProperty("user.dir") + "/xml_files/xsl_transformations/";
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String xmlFile = FILENAME + "hello_xsl.xml", 
				xslFile = FILENAME + "hello_xlst.xsl";
		
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = 
                factory.newTransformer( new StreamSource( xslFile ) );
            
            StreamSource xmlsource = new StreamSource( xmlFile );
            StreamResult output = new StreamResult( System.out );
            transformer.transform( xmlsource, output );

			
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
		
		
	}

}

// anothertext