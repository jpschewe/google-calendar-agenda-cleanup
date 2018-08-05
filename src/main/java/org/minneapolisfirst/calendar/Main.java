package org.minneapolisfirst.calendar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.filter.IEventFilter;
import com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;

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

				final ITextExtractionStrategy strategy = new FilteredTextEventListener(
						new LocationTextExtractionStrategy(), new TextFilter());

				final String newText = PdfTextExtractor.getTextFromPage(page, strategy);
				LOGGER.info("new text '{}'", newText);

//				PdfDictionary dict = page.getPdfObject();
//				PdfObject object = dict.get(PdfName.Contents);

//				if (object instanceof PdfStream) {
//					PdfStream stream = (PdfStream) object;
//					byte[] data = stream.getBytes();
//					final String str = new String(data);
//					LOGGER.info("Saw text '{}'", str);
//					// stream.setData(new String(data).replace("Hello World", "HELLO
//					// WORLD").getBytes("UTF-8"));
//				} else {
//					LOGGER.info("Found object of type {}", object.getClass());
//				}

				destDoc.setDefaultPageSize(new PageSize(pageSize));
				destDoc.addNewPage();
			}

		}
	}

	public class TextFilter implements IEventFilter {

		private float filterY = Float.NaN;

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
					return false;
				}
			}

			return true;
		}

	}

}
