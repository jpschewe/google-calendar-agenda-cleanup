package org.minneapolisfirst.calendar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FilenameUtils;
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
		final JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(getInitialDirectory());
		chooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
		chooser.setDialogTitle("Choose PDF calendar to clean up");

		final int result = chooser.showOpenDialog(null);
		if (JFileChooser.APPROVE_OPTION == result) {
			final File selected = chooser.getSelectedFile();
			setInitialDirectory(selected);

			final Path src = Paths.get(selected.toURI());

			final String basename = FilenameUtils.getBaseName(src.toString());
			final String cleanName = String.format("%s.clean.pdf", basename);

			final Path dest = src.getParent().resolve(cleanName);

			new Main(src, dest);
		}
		System.exit(0);
	}

	/**
	 * Set the initial directory preference. This supports opening new file dialogs
	 * to a (hopefully) better default in the user's next session.
	 * 
	 * @param dir the File for the directory in which file dialogs should open
	 */
	private static void setInitialDirectory(final File dir) {
		// Store only directories
		final File directory;
		if (dir.isDirectory()) {
			directory = dir;
		} else {
			directory = dir.getParentFile();
		}

		final Preferences preferences = Preferences.userNodeForPackage(Main.class);
		final String previousPath = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

		if (!directory.toString().equals(previousPath)) {
			preferences.put(INITIAL_DIRECTORY_PREFERENCE_KEY, directory.toString());
		}
	}

	/**
	 * Get the initial directory to which file dialogs should open. This supports
	 * opening to a better directory across sessions.
	 * 
	 * @return the File for the initial directory
	 */
	private static File getInitialDirectory() {
		final Preferences preferences = Preferences.userNodeForPackage(Main.class);
		final String path = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

		File dir = null;
		if (null != path) {
			dir = new File(path);
		}
		return dir;
	}

	/**
	 * Preferences key for file dialog initial directory
	 */
	private static final String INITIAL_DIRECTORY_PREFERENCE_KEY = "InitialDirectory";

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
