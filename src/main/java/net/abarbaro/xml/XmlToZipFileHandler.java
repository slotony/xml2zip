package net.abarbaro.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A SAX handler that populates a given zip file from an XML stream.  Each entry
 * within the zip will contain a certain maximum number of elements from the
 * source XML file.  The splitting of the source XML into multiple ZipEntry's
 * is based on an element name to split on. All front matter leading to that
 * element name is repeated within ZipEntry.
 * <ol><u>Schema Limitations</u>:
 * <li>The children of the root element must be of a common complexType
 * <li>The splitOnElementName may only occur once within that complexType and its descendants
 * </ol>
 * Usage:<br><pre>
 * 	SAXParserFactory saxpf = SAXParserFactory.newInstance();
 * 		<tab>saxpf.setNamespaceAware(true);
 * 		<tab>saxpf.setValidating(true);
 *
 * 		<tab>SAXParser saxp = saxpf.newSAXParser();
 *
 * 		<tab>saxp.parse(XML_FILE_IN, new XmlToZipFileHandler("record", 1000, ZIP_FILE_OUT, "ZipEntry"));
 * </pre>
 */
public class XmlToZipFileHandler extends DefaultHandler {

    private final Logger logger = LoggerFactory.getLogger(XmlToZipFileHandler.class);
    /*
     * The output zip file to be populated by split XML files
     */
    private final File zipFile;
    /*
     * The local name of the XML element to split on
     */
    private final String splitOnElementName;
    /*
     * The stem part of the ZipEntry name
     */
    private final String zipEntryStem;
    /*
     * Capture the XML content to be repeated in zipEntry
     */
    private final StringBuilder rootElementOpen = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    private final StringBuilder frontMatter;
    /*
     * Keep track of nesting between the root and elements to split on.
     */
    private final Stack<String> stack;
    /*
     * The maximum number of elements that can be copied into zipEntry
     */
    private final int batchSize;
    private FileOutputStream fos = null;
    private BufferedOutputStream bos = null;
    private ZipOutputStream out = null;
    private boolean isFirstElement = true;
    private boolean inFrontMatter;
    /*
     * The count of split-on-elements streamed to the zipEntry for the current
     * split so far.
     */
    private int count;

    /*
     * The count of zipEntry created so far
     */
    private int zipEntryCount;

    /**
     * Constructor
     *
     * @param splitOnElementName The element name to split on.
     * @param batchSize          The number of elements of that name to be streamed to an XML file.
     * @param zipFile            A reference to the zip file that will receive the split XML files.
     * @param zipEntryStem       The leading part of the name to assign to a <code>ZipEntry</code>
     */
    public XmlToZipFileHandler(String splitOnElementName, int batchSize, File zipFile, String zipEntryStem) {

        logger.trace("Entering");

        this.zipFile = zipFile;
        this.batchSize = batchSize;
        this.splitOnElementName = splitOnElementName;
        this.zipEntryStem = zipEntryStem;
        this.stack = new Stack<>();
        this.frontMatter = new StringBuilder();

        logger.trace("Exiting");
    }

    /**
     * Initialize zip output
     *
     * @throws SAXException
     */
    @Override
    public void startDocument() throws SAXException {

        logger.trace("Entering");

        try {
            fos = new FileOutputStream(zipFile);
            bos = new BufferedOutputStream(fos);
            out = new ZipOutputStream(bos);
        } catch (IOException e) {
            logger.error("Error creating the zip file!", e);
            throw new SAXException("Error creating the zip file!");
        } finally {
            logger.trace("Exiting");
        }
    }

    /**
     * Close all zip output resources
     *
     * @throws SAXException
     */
    @Override
    public void endDocument() throws SAXException {

        logger.trace("Entering");

        if (!stack.isEmpty())

            logger.warn("Items left on the stack: ");
            stack.stream().forEach(s -> logger.debug(s));

        try {
            out.close();
            bos.close();
            fos.close();
        } catch (IOException e) {
            logger.error("Error closing zip file!", e);
            throw new SAXException("Error closing the zip file!");
        } finally {
            logger.trace("Exiting");
        }
    }


    /**
     * <ul>Writes the start element to the current <code>ZipEntry</code>, and:
     * <li>Caches content in in this <code>rootElementOpen</code> for creation of subsequent <code>ZipEntry</code>.
     * <li>Configures new <code>ZipEntry</code> when the batch limit on the previous <code>ZipEntry</code> has been
     * reached.
     * </ul>
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        logger.trace("Entering");

        if (isFirstElement) {

            isFirstElement = false;

            rootElementOpen.append('<').append(localName).append(" xmlns=\"").append(uri).append('"').append('>');
            stack.push(localName);

            inFrontMatter = true;

            try {
                zipEntryCount++;
                ZipEntry nextEntry = createZipEntry();
                out.putNextEntry(nextEntry);
                out.write(rootElementOpen.toString().getBytes());
                logger.debug("Put {} ", nextEntry.getName());
            } catch (IOException e) {
                logger.error("Error writing to the zip file!", e);
                throw new SAXException("Error writing to the zip file!");
            }

            return;

        } else if (splitOnElementName.equals(localName)) {

            count++;

            if (count > batchSize) {

                count = 1;

                if (!stack.isEmpty()) {

                    Stack<String> reverseStack = new Stack<>();

                    while (!stack.isEmpty()) {

                        String s = stack.pop();

                        try {
                            out.write('<');
                            out.write('/');
                            out.write(s.getBytes());
                            out.write('>');
                            out.flush();

                        } catch (IOException e) {
                            logger.error("Error closing the this zipEntry!", e);
                            throw new SAXException("Error closing this zipEntry");
                        } finally {
                            reverseStack.push(s);
                        }
                    }

                    while (!reverseStack.isEmpty()) {
                        stack.push(reverseStack.pop());
                    }

                }

                try {
                    zipEntryCount++;
                    ZipEntry nextEntry = createZipEntry();
                    out.putNextEntry(nextEntry);

                    logger.debug("Put " + nextEntry.getName());

                } catch (IOException e) {
                    logger.error("Error opening a new zipEntry!", e);
                    throw new SAXException("Error opening a new ZipEntry!");
                }

                try {
                    out.write(rootElementOpen.toString().getBytes());
                    out.write(frontMatter.toString().getBytes());
                } catch (IOException e) {
                    logger.error("Error writing to the new zipEntry!", e);
                    throw new SAXException("Error writing to the new ZipEntry");
                }

            }

            inFrontMatter = false;

            try {
                out.write('<');
                out.write(localName.getBytes());
                out.write('>');
            } catch (IOException e) {
                logger.error("Error starting element!", e);
                throw new SAXException("Error starting element!");
            }

        } else {

            try {
                out.write('<');
                out.write(localName.getBytes());
                out.write('>');
            } catch (IOException e) {
                logger.error("Error starting element!", e);
                throw new SAXException("Error starting element!", e);
            }
        }

        if (inFrontMatter) {

            frontMatter.append('<').append(localName).append('>');

        }

        stack.push(localName);

        logger.trace("Exiting");

    }

    /**
     * Writes the end element to the current <code>ZipEntry</code>.
     * If at a split point, first writes this frontMatter to the current <code>ZipEntry</code>.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        logger.trace("Entering");


        if (!stack.isEmpty() && stack.peek().equals(localName)) {

            stack.pop();

        }

        if (inFrontMatter) {
            frontMatter.append('<').append('/').append(localName).append('>');
        }

        try {
            out.write('<');
            out.write('/');
            out.write(localName.getBytes());
            out.write('>');
        } catch (IOException e) {
            logger.error("Error ending element!", e);
            throw new SAXException("Error ending element!");
        } finally {

            logger.trace("Exiting");

        }

    }

    /**
     * Writes the current <code>char[]</code> to the current <code>ZipEntry</code>.
     * If at a split point, first writes this <code>frontMatter</code> to the current <code>ZipEntry</code>.
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        logger.trace("Entering");


        if (inFrontMatter) {
            frontMatter.append(new String(ch, start, length));
        }
        try {
            out.write(new String(ch, start, length).getBytes());
        } catch (IOException e) {
            logger.error("Error writing characters!", e);
            throw new SAXException("Error writing characters!");
        } finally {
            logger.trace("Exiting");

        }
    }
    /*
    Create a new ZipEntry based on this zipEntryStem and zipEntryCount
     */
    private ZipEntry createZipEntry() {
        return new ZipEntry(String.format("%s-%04d.xml", zipEntryStem, zipEntryCount));
    }
}
