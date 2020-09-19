# xml2zip

Java application to split XML into multiple zip entries 

  A SAX handler that populates a given zip file from an XML stream.  Each entry
  within the zip will contain a certain maximum number of elements from the
  source XML file.  The splitting of the source XML into multiple zip entries 
  is based on an element name to split on. All front matter leading to that
  element name is repeated within each ZipEntry.

  Schema Limitations:
   * The children of the root element must be of a common complexType
   * The splitOnElementName may only occur once within that complexType and its descendants

  Usage:<pre><code>
    SAXParserFactory saxpf = SAXParserFactory.newInstance();
        
        saxpf.setNamespaceAware(true);
        saxpf.setValidating(true);

        SAXParser saxp = saxpf.newSAXParser();

        saxp.parse(XML_FILE_IN, new XmlToZipFileHandler("record", 1000, ZIP_FILE_OUT, "ZipEntry"));
