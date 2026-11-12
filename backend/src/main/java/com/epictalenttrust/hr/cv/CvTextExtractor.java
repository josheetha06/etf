package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.common.ApiException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Map;

/** Detects the real file type (not just the extension) and pulls plain text out of PDF and Word CVs. */
@Component
public class CvTextExtractor {

    public record Extracted(String text, String contentType, String extension) {
    }

    private static final int MAX_CHARS = 500_000;
    private static final Map<String, String> ALLOWED = Map.of(
            "application/pdf", "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/msword", "doc",
            "application/x-tika-msoffice", "doc");

    private final Tika detector = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public Extracted extract(byte[] content, String fileName) {
        String type = detector.detect(content, fileName);
        String extension = ALLOWED.get(type);
        if (extension == null) {
            throw ApiException.badRequest("unsupported_file", "Only PDF and Word files can be added.");
        }
        String text = parse(content, fileName);
        if (text == null || text.strip().length() < 40) {
            throw ApiException.badRequest("no_text", "This CV has no readable text. It may be a scanned image.");
        }
        return new Extracted(text, type, extension);
    }

    private String parse(byte[] content, String fileName) {
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();
        PDFParserConfig pdf = new PDFParserConfig();
        // Read PDFs in visual order so right-aligned dates stay on the same line as their job or degree.
        pdf.setSortByPosition(true);
        pdf.setExtractInlineImages(false);
        context.set(PDFParserConfig.class, pdf);
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            parser.parse(stream, handler, metadata, context);
        } catch (SAXException ex) {
            if (!WriteLimitReachedException.isWriteLimitReached(ex)) {
                throw unreadable();
            }
        } catch (IOException | TikaException ex) {
            throw unreadable();
        }
        return handler.toString();
    }

    private static ApiException unreadable() {
        return ApiException.badRequest("unreadable_file",
                "This file couldn't be opened. It may be damaged or password-protected.");
    }
}
