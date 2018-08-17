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

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;

public class Main {

	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) throws FileNotFoundException, IOException {
		final Path src = Paths.get("/home/jpschewe/Downloads/calendar_2018-08-04_2018-08-19.pdf");
		final Path dest = Paths.get("/home/jpschewe/Downloads/calendar_clean.pdf");

		new Main(src, dest);

	}

	private Main(final Path src, final Path dest) throws FileNotFoundException, IOException {

		try (PdfDocument srcDoc = new PdfDocument(new PdfReader(src.toFile()));
				PdfDocument destDoc = new PdfDocument(new PdfWriter(dest.toFile()))) {

			for (int i = 1; i <= srcDoc.getNumberOfPages(); ++i) {
				final PdfPage page = srcDoc.getPage(i);

				final GatherBaselines gatherBaselines = new GatherBaselines();
				final PdfCanvasProcessor processor = new PdfCanvasProcessor(gatherBaselines);
				processor.processPageContent(page);

				LOGGER.debug("Filter baselines for page {} -> {}", i, gatherBaselines.baselinesToFilter);

				final PdfPage destPage = page.copyTo(destDoc);
				final FilterEventsByBaseline filterRects = new FilterEventsByBaseline(gatherBaselines.baselinesToFilter,
						destPage);
				final PdfCanvasProcessor processor2 = new PdfCanvasProcessor(filterRects);
				processor2.processPageContent(destPage);

				destDoc.addPage(destPage);
			}

		}
	}

	public class FilterEventsByBaseline implements IEventListener {
		private final List<Float> baselinesToFilter;
		private final PdfPage page;
		private final PdfCanvas canvas;

		public FilterEventsByBaseline(final List<Float> baselinesToFilter, final PdfPage page) {
			this.baselinesToFilter = baselinesToFilter;
			this.page = page;
			canvas = new PdfCanvas(this.page);
		}

		private class BoundingRect {
			float minX = Float.MAX_VALUE;
			float minY = Float.MAX_VALUE;
			float maxX = 0;
			float maxY = 0;

			public void add(final LineSegment line) {
				add(line.getStartPoint());
				add(line.getEndPoint());
			}

			public void add(final Vector v) {
				minX = Math.min(minX, v.get(0));
				maxX = Math.max(maxX, v.get(0));

				minY = Math.min(minY, v.get(1));
				maxY = Math.max(maxY, v.get(1));
			}

			public Rectangle toRectangle() {
				return new Rectangle(minX, minY, maxX - minX, maxY - minY);
			}

			@Override
			public String toString() {
				return String.format("%f, %f - %f, %f", minX, minY, maxX, maxY);
			}
		}

		@Override
		public void eventOccurred(final IEventData data, final EventType type) {
			if (type.equals(EventType.RENDER_TEXT)) {
				final TextRenderInfo renderInfo = (TextRenderInfo) data;
				final LineSegment baseline = renderInfo.getBaseline();
				final float checkY = baseline.getStartPoint().get(1);

				final boolean filter = baselinesToFilter.stream().anyMatch(f -> Math.abs(checkY - f) < 1E-6);
				if (filter) {
					final BoundingRect bounds = new BoundingRect();
					bounds.add(renderInfo.getAscentLine());
					bounds.add(renderInfo.getDescentLine());
					bounds.add(renderInfo.getBaseline());

					LOGGER.debug("Filter {}", bounds);

					canvas.setStrokeColor(ColorConstants.WHITE).setFillColor(ColorConstants.WHITE)
							.rectangle(bounds.toRectangle()).fillStroke();
				}
			}
		}

		@Override
		public Set<EventType> getSupportedEvents() {
			return Collections.singleton(EventType.RENDER_TEXT);
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
