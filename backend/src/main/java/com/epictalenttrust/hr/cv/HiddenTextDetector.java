package com.epictalenttrust.hr.cv;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Finds text in a PDF that a reader can't see but screening software reads: invisible text, text smaller than
 * 1.5 pt, and text placed outside the page. White text is not counted, because many CV templates put white text on
 * a coloured sidebar.
 */
@Component
public class HiddenTextDetector {

    private static final Logger log = LoggerFactory.getLogger(HiddenTextDetector.class);
    private static final int MIN_HIDDEN_CHARACTERS = 40;
    private static final int MAX_PAGES = 10;

    /** A sample of the hidden text, when there's enough of it to matter. */
    public Optional<String> hiddenText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted() || document.getNumberOfPages() > MAX_PAGES) {
                return Optional.empty();
            }
            Collector collector = new Collector();
            collector.setSortByPosition(false);
            collector.writeText(document, new StringWriter());
            if (collector.hidden.length() >= MIN_HIDDEN_CHARACTERS) {
                String sample = collector.hidden.toString().replaceAll("\\s+", " ").strip();
                return Optional.of(sample.length() > 160 ? sample.substring(0, 160) + "…" : sample);
            }
            return Optional.empty();
        } catch (IOException | RuntimeException ex) {
            log.debug("Hidden-text check skipped: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static final class Collector extends PDFTextStripper {

        private final StringBuilder hidden = new StringBuilder();
        private PDRectangle box;

        Collector() {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            box = page.getCropBox();
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            boolean invisible = getGraphicsState().getTextState().getRenderingMode() == RenderingMode.NEITHER;
            boolean tiny = text.getFontSizeInPt() > 0 && text.getFontSizeInPt() < 1.5f;
            boolean offPage = box != null && (text.getX() < -5 || text.getY() < -5
                    || text.getX() > box.getWidth() + 5 || text.getY() > box.getHeight() + 5);
            if ((invisible || tiny || offPage) && hidden.length() < 2000) {
                hidden.append(text.getUnicode());
            }
            super.processTextPosition(text);
        }
    }
}
