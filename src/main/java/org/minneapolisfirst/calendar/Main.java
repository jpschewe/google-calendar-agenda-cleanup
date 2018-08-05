package org.minneapolisfirst.calendar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.filter.IEventFilter;
import com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;

public class Main {

	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
		final Path src = Paths.get("/home/jpschewe/Downloads/calendar_2018-08-04_2018-08-19.pdf");
		final Path dest = Paths.get("/home/jpschewe/Downloads/calendar_clean.pdf");

		final Main app = new Main(src, dest);

	}

	private Main(final Path src, final Path dest) throws FileNotFoundException, IOException {

		try (PdfDocument srcDoc = new PdfDocument(new PdfReader(src.toFile()));
				PdfDocument destDoc = new PdfDocument(new PdfWriter(dest.toFile()))) {
			final Rectangle pageSize = srcDoc.getFirstPage().getPageSize();

			for (int i = 1; i <= srcDoc.getNumberOfPages(); ++i) {
				PdfPage page = srcDoc.getPage(i);

//				final ITextExtractionStrategy strategy = new SimpleTextExtractionStrategy();
//		        final String currentText = PdfTextExtractor.getTextFromPage(page, strategy);
//		        LOGGER.info("Found text for page {} -> '{}'", i, currentText);

				final GatherBaselines gatherBaselines = new GatherBaselines();
				final ITextExtractionStrategy strategy = new FilteredTextEventListener(
						new LocationTextExtractionStrategy(), gatherBaselines);

				// need to walk the file, using a text extractor seems to work
				// TODO: might be a better way to do this
				PdfTextExtractor.getTextFromPage(page, strategy);
				// LOGGER.info("new text '{}'", newText);

				destDoc.setDefaultPageSize(new PageSize(pageSize));
				destDoc.addNewPage();
			}

		}
	}

	public class FilterEventsByBaseline implements IEventFilter {
		private final List<Float> baselinesToFilter;

		public FilterEventsByBaseline(final List<Float> baselinesToFilter) {
			this.baselinesToFilter = baselinesToFilter;
		}

		@Override
		public boolean accept(final IEventData data, final EventType type) {
			if (type.equals(EventType.RENDER_TEXT)) {
				final TextRenderInfo renderInfo = (TextRenderInfo) data;
				final LineSegment baseline = renderInfo.getBaseline();
				final float checkY = baseline.getStartPoint().get(1);

				final boolean filter = baselinesToFilter.stream().anyMatch(f -> Math.abs(checkY - f) < 1E-6);
				return !filter;
			}

			return true;

		}
	}

	public class GatherBaselines implements IEventFilter {

		// need to store all baselines that are problems
		// the assumption is that all RENDER_TEXT operations with a baseline in the bad
		// list need to be filtered when copying pages
		private float filterY = Float.NaN;
		private final List<Float> baselinesToFilter = new LinkedList<>();

		@Override
		public boolean accept(final IEventData data, final EventType type) {
			if (type.equals(EventType.RENDER_TEXT)) {
				final TextRenderInfo renderInfo = (TextRenderInfo) data;

				final String text = renderInfo.getText();
				final LineSegment baseline = renderInfo.getBaseline();
				if (Float.isFinite(filterY)) {
					if (Math.abs(filterY - baseline.getStartPoint().get(1)) < 1E-6) {
						LOGGER.info("Filtering '{}'", text);
						return false;
					} else {
						// new line
						filterY = Float.NaN;
					}
				}

				if (null != text && (text.contains("Calendar:") || text.contains("Created by:"))) {
					LOGGER.info("Filtering '{}'", text);
					filterY = baseline.getStartPoint().get(1);
					// index 1 is the y coordinate
					baselinesToFilter.add(baseline.getStartPoint().get(1));
					return false;
				}
			}

			return true;
		}

	}

}
