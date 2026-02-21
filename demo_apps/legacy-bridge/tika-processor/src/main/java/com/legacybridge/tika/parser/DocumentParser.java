package com.legacybridge.tika.parser;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps Apache Tika functionality for document text extraction, metadata extraction,
 * and MIME type detection. Uses AutoDetectParser to handle a wide range of document
 * formats including PDF, DOCX, XLSX, and more.
 */
public class DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParser.class);

    private final Tika tika;
    private final Parser parser;

    public DocumentParser() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        logger.info("DocumentParser initialized with AutoDetectParser");
    }

    /**
     * Extracts text content from the given input stream using Apache Tika.
     * Supports a wide range of document formats (PDF, DOCX, XLSX, HTML, etc.).
     *
     * @param input the input stream containing the document bytes
     * @return the extracted text content
     * @throws IOException   if an I/O error occurs
     * @throws TikaException if Tika fails to parse the document
     */
    public String extractText(InputStream input) throws IOException, TikaException {
        logger.debug("Starting text extraction");
        long startTime = System.currentTimeMillis();

        try {
            // Use BodyContentHandler with a generous limit (-1 = unlimited)
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            parseContext.set(Parser.class, parser);

            parser.parse(input, handler, metadata, parseContext);

            String extractedText = handler.toString();
            long elapsed = System.currentTimeMillis() - startTime;

            logger.info("Text extraction complete in {} ms, extracted {} characters",
                    elapsed, extractedText.length());
            logger.debug("Detected content type: {}", metadata.get(Metadata.CONTENT_TYPE));

            return extractedText;

        } catch (SAXException e) {
            logger.error("SAX error during text extraction: {}", e.getMessage(), e);
            throw new TikaException("SAX parsing error", e);
        }
    }

    /**
     * Extracts metadata from the given input stream using Apache Tika.
     * Returns a map of metadata key-value pairs.
     *
     * @param input the input stream containing the document bytes
     * @return a map of metadata entries
     * @throws IOException   if an I/O error occurs
     * @throws TikaException if Tika fails to parse the document
     */
    public Map<String, String> extractMetadata(InputStream input) throws IOException, TikaException {
        logger.debug("Starting metadata extraction");
        long startTime = System.currentTimeMillis();

        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            parseContext.set(Parser.class, parser);

            parser.parse(input, handler, metadata, parseContext);

            Map<String, String> metadataMap = new LinkedHashMap<>();
            for (String name : metadata.names()) {
                metadataMap.put(name, metadata.get(name));
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Metadata extraction complete in {} ms, found {} entries",
                    elapsed, metadataMap.size());
            logger.debug("Metadata entries: {}", metadataMap.keySet());

            return metadataMap;

        } catch (SAXException e) {
            logger.error("SAX error during metadata extraction: {}", e.getMessage(), e);
            throw new TikaException("SAX parsing error during metadata extraction", e);
        }
    }

    /**
     * Detects the MIME type of the given input stream using Tika's detection mechanism.
     *
     * @param input the input stream containing the document bytes
     * @return the detected MIME type string (e.g., "application/pdf")
     * @throws IOException if an I/O error occurs
     */
    public String detectType(InputStream input) throws IOException {
        logger.debug("Starting MIME type detection");
        long startTime = System.currentTimeMillis();

        String detectedType = tika.detect(input);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("MIME type detection complete in {} ms: {}", elapsed, detectedType);

        return detectedType;
    }
}
