package org.minneapolisfirst.calendar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.filter.IEventFilter;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;

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
				final PdfCanvasProcessor processor = new PdfCanvasProcessor(gatherBaselines);
				processor.processPageContent(page);

				LOGGER.info("Filter baselines for page {} -> {}", i, gatherBaselines.baselinesToFilter);

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

	public class GatherBaselines implements IEventListener {

		// need to store all baselines that are problems
		// the assumption is that all RENDER_TEXT operations with a baseline in the bad
		// list need to be filtered when copying pages
		private final List<Float> baselinesToFilter = new LinkedList<>();

		@Override
		public void eventOccurred(final IEventData data, final EventType type) {
			if (type.equals(EventType.RENDER_TEXT)) {
				final TextRenderInfo renderInfo = (TextRenderInfo) data;

				final String text = renderInfo.getText();
				final LineSegment baseline = renderInfo.getBaseline();
				if (null != text && (text.contains("Calendar:") || text.contains("Created by:"))) {
					// index 1 is the y coordinate
					baselinesToFilter.add(baseline.getStartPoint().get(1));
				}
			}

		}
		
		@Override
		public Set<EventType> getSupportedEvents() {
			return Collections.singleton(EventType.RENDER_TEXT);
		}

	}

}
