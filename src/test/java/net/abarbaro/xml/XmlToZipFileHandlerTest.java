package net.abarbaro.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XmlToZipFileHandlerTest {
	
	// Where input and output goes
	static final File RESOURCE_PATH = new File("./src/test/resources");
	
	// The XML source file to split.  It has 100 <record> elements
	static final File XML_FILE_IN = new File(RESOURCE_PATH, "Records.xml");

	// The zip file produced by this.
	static final File ZIP_FILE_OUT = new File(RESOURCE_PATH, "Records.zip");
	
	// The names of the XML files withing this zip file
	static final String ZIP_ENTRY_NAME_PRE = "ZipEntry";
	
	// The element name in the source XML to track in terms of split
	static final String ELEMENT_NAME_TO_SPLIT_ON = "record";
	
	// The max number of <record> elements in a ZipEntry
	static final int BATCH_SIZE = 10;
	
	// The SAX handler class under test
	XmlToZipFileHandler classUnderTest;
	
	
	/**
	 * Use this classUnderTest to parse the source XML file and produce a zip.
	 * The zip file will be analyzed in subsequent tests.
	 * @throws Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		assertTrue("Resource path " + RESOURCE_PATH.getAbsolutePath() + " exists?", RESOURCE_PATH.exists());
		assertTrue("Resource  path \" + RESOURCE_PATH.getAbsolutePath()" + " can be written to?", RESOURCE_PATH.canWrite());
		assertTrue("XMLinput file " + XML_FILE_IN + " exists?", XML_FILE_IN.exists());
		

		// Delete zip file from prior test
		if(ZIP_FILE_OUT.exists()) {			
			assertTrue(ZIP_FILE_OUT.delete());
		}
		

		SAXParserFactory saxpf = SAXParserFactory.newInstance();
		saxpf.setNamespaceAware(true);
		saxpf.setValidating(true);

		SAXParser saxp = saxpf.newSAXParser();

		saxp.parse(XML_FILE_IN, new XmlToZipFileHandler(ELEMENT_NAME_TO_SPLIT_ON, BATCH_SIZE, ZIP_FILE_OUT, ZIP_ENTRY_NAME_PRE));
		
		assertTrue("Zip file exists?", ZIP_FILE_OUT.exists());
		assertTrue("Can read zip file?", ZIP_FILE_OUT.canRead());
	}

	
	/**
	 * Analyze the zip file. <ul>Confirm:
	 * <li>Number of ZipEntries
	 * <li>Name of each ZipEntry
	 * <li>Number of records within each entry
	 * <li>Presence of the frontMatter element in each entry
	 * </ul>
	 * @throws Exception
	 */
	@Test 
	public void testZipFile() throws Exception {
	
		
		final DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		final XPathFactory xpFact = XPathFactory.newInstance();

		class Counter {
			int count;
			void increment() {count++;}
			
		}
		
		final Counter zeCounter = new Counter();
		
		try (ZipFile zipFile = new ZipFile(ZIP_FILE_OUT)) {
			
			zipFile.stream().forEach(ze -> {

				zeCounter.increment();
				
				assertEquals(String.format("%s-%04d.xml", ZIP_ENTRY_NAME_PRE, zeCounter.count), ze.getName());
	
				assertTrue(ze.getSize() > 0);
				
				
				String recordCount = null;
				String frontMatterCount = null;
				
				try (InputStream is = zipFile.getInputStream(ze)) {
					
					Document doc = parser.parse(is);
					
					
					frontMatterCount = xpFact.newXPath().compile("count(/records/frontMatter)").evaluate(doc);
					assertEquals(1, Integer.parseInt(frontMatterCount));
					
					recordCount = xpFact.newXPath().compile("count(/records/nested/record)").evaluate(doc);
					
					assertEquals(10, Integer.parseInt(recordCount));
					
				} catch (XPathExpressionException | IOException | SAXException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				
			});
			
			assertEquals(10, zeCounter.count);
			
		} catch (IOException e ) { 
			e.printStackTrace();
		} finally {
			
		}
		
	}

}
