package ai.simplfin.doczbot.services.masking;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ai.simplfin.doczbot.dto.AadhaarLocationDto;
import ai.simplfin.doczbot.dto.IdDocumentExtractionResult;
import ai.simplfin.doczbot.dto.NumWordDto;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

@Service
@Slf4j
public class AadhaarMaskingService {

	private static final Logger log = LoggerFactory.getLogger(AadhaarMaskingService.class);

	@Autowired
	private AadhaarDetectionService aadhaarDetectionService;
	
	@Autowired
	private  ITesseract tesseract;
	
	@Autowired
	private ImageOrientationService imageOrientationService;
	
	@Autowired
	private PythonTextReplacementService pythonTextReplacementService;  // NEW

	private final Path uploadDir;
	private final Path outputDir;

	@Value("${app.base.url:http://localhost:8080}")
	private String baseUrl;

	@Value("${app.masked.files.path:/masked}")
	private String maskedFilesPath;
	
	@Value("${masking.use.python:true}")  // NEW: Toggle Python masking
	private boolean usePythonMasking;

	public enum MaskingType {
		ASTERISK("*"), X("X"), RIBBON("RIBBON");

		private final String value;

		MaskingType(String value) {
			this.value = value;
		}

		public static MaskingType fromString(String value) {
			if (value == null) {
				return RIBBON;
			}
			for (MaskingType type : MaskingType.values()) {
				if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
					return type;
				}
			}
			return RIBBON;
		}
	}

	/**
	 * SIMPLE & RELIABLE FIX: Use OCR text to validate Aadhaar patterns
	 * Instead of complex geometric checks, extract the text line and validate
	 */


	
	public AadhaarMaskingService() throws IOException {
//		this.tesseract = new Tesseract();
//		this.tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
//		this.tesseract.setLanguage("eng");
//		this.tesseract.setPageSegMode(11);
//		this.tesseract.setOcrEngineMode(3);

		this.uploadDir = Paths.get("src/main/resources/templates");
		this.outputDir = Paths.get("src/main/resources/masked");
		Files.createDirectories(uploadDir);
		Files.createDirectories(outputDir);
	}

	public IdDocumentExtractionResult maskAadhaarInDocument(MultipartFile file, String maskingTypeStr)
			throws Exception {
		long startTime = System.currentTimeMillis();
		String contentType = file.getContentType();
		String originalFilename = file.getOriginalFilename();
		LocalDateTime extractionTime = LocalDateTime.now();

		MaskingType maskingType = MaskingType.fromString(maskingTypeStr);
		log.info("Processing with masking type: {} (Python mode: {})", maskingType, usePythonMasking);

		if (contentType == null || !isValidFileType(contentType)) {
			return buildErrorResult(originalFilename, contentType, extractionTime,
					System.currentTimeMillis() - startTime, "Unsupported file type", null);
		}

		IdDocumentExtractionResult result;
		try {
			if (contentType.equalsIgnoreCase("application/pdf")) {
				try (InputStream is = file.getInputStream()) {
					result = processPdf(is, originalFilename, contentType, extractionTime, startTime, maskingType);
				}
			} else if (contentType.startsWith("image/")) {
			    BufferedImage image = ImageIO.read(file.getInputStream());
			    if (image == null) {
			        return buildErrorResult(
			            originalFilename, contentType, extractionTime,
			            System.currentTimeMillis() - startTime,
			            "Invalid or corrupted image file", "DATA_NOT_FOUND"
			        );
			    }
			    String extension = getFileExtension(originalFilename);
			    result = processImageInternal(image, originalFilename, contentType, extractionTime, startTime, extension, maskingType);
			} else {
				result = buildErrorResult(originalFilename, contentType, extractionTime,
						System.currentTimeMillis() - startTime, "Unsupported file format", "INVALID_FILE_FORMAT");
			}
		} catch (Exception e) {
			log.error("Error processing document", e);
			result = buildErrorResult(originalFilename, contentType, extractionTime,
					System.currentTimeMillis() - startTime, "Processing error: " + e.getMessage(), null);
		}

		return result;
	}

	private boolean isValidFileType(String contentType) {
		return contentType.contains("pdf") || contentType.contains("image") || contentType.contains("jpeg")
				|| contentType.contains("png") || contentType.contains("tiff") || contentType.contains("bmp");
	}

	/**
	 * COMPLETE FIX: Process ALL pages in PDF with proper validation
	 * Replace your entire processPdf() method with this
	 */
	private IdDocumentExtractionResult processPdf(InputStream is, String originalFilename, String contentType,
	        LocalDateTime extractionTime, long startTime, MaskingType maskingType) throws Exception {

	    PDDocument document = PDDocument.load(is);
	    PDFRenderer renderer = new PDFRenderer(document);

	    List<BufferedImage> maskedImages = new ArrayList<>();
	    List<AadhaarLocationDto> allLocations = new ArrayList<>();
	    boolean foundAnyAadhaar = false;

	    int totalPages = document.getNumberOfPages();
	    System.out.println("\n" + "=".repeat(80));
	    System.out.println("📄 PDF PROCESSING STARTED");
	    System.out.println("   File: " + originalFilename);
	    System.out.println("   Total Pages: " + totalPages);
	    System.out.println("   Masking Type: " + maskingType);
	    System.out.println("=".repeat(80));

	    // CRITICAL: Process EACH page in the loop
	    for (int pageNum = 0; pageNum < totalPages; pageNum++) {
	        int currentPage = pageNum + 1;
	        
	        System.out.println("\n" + "─".repeat(80));
	        System.out.println("📄 PROCESSING PAGE " + currentPage + " of " + totalPages);
	        System.out.println("─".repeat(80));
	        
	        log.info("Processing PDF page {}/{}", currentPage, totalPages);

	        try {
	            // Step 1: Render this page
	            BufferedImage image = renderer.renderImageWithDPI(pageNum, 300);
	            System.out.println("✓ Rendered page at 300 DPI (" + image.getWidth() + "x" + image.getHeight() + ")");

	            // Step 2: Correct orientation for this page
	            BufferedImage correctedImage = imageOrientationService.smartOrientationCorrection(image);
	            System.out.println("✓ Orientation correction completed");

	            // Step 3: Extract text from this page
	            System.out.println("\n=== OCR EXTRACTED TEXT (Page " + currentPage + ") ===");
	            String extractedText = checkForAadhaarWithText(correctedImage);
	            System.out.println("EXTRACTED TEXT::  "+extractedText);
	            extractedText = extractedText != null ? extractedText.trim() : "";

	            // Split and trim each OCR line
	            List<String> ocrLines = Arrays.stream(extractedText.split("\\r?\\n"))
	                    .map(String::trim)
	                    .filter(line -> !line.isEmpty()) // optional: remove blank lines
	                    .toList();

	            System.out.println(extractedText != null ? extractedText : "[No text detected]");
	            System.out.println("=== END OCR TEXT ===");

	            // Step 4: CRITICAL - Detect Aadhaar on THIS page using AadhaarDetectionService
	            System.out.println("\n🔍 Starting Aadhaar detection for page " + currentPage + "...\n");
	            
//	            List<AadhaarLocationDto> locations = aadhaarDetectionService.findAadhaarNumbers(correctedImage);
	            List<AadhaarLocationDto> locations = aadhaarDetectionService.findAadhaarNumbers(correctedImage, ocrLines);

	            System.out.println("\n📊 Page " + currentPage + ": Found " + locations.size() + " Aadhaar number(s)");

	            if (!locations.isEmpty()) {
	                foundAnyAadhaar = true;
	                allLocations.addAll(locations);

	                // Display what was found
	                for (int i = 0; i < locations.size(); i++) {
	                    AadhaarLocationDto loc = locations.get(i);
	                    String displayNum = loc.getUid().substring(0, 4) + " " + 
	                                       loc.getUid().substring(4, 8) + " " + 
	                                       loc.getUid().substring(8, 12);
	                    System.out.println("   Instance " + (i + 1) + ": " + displayNum + 
	                                     " at position (" + loc.getX() + ", " + loc.getY() + ")");
	                }

	                // Mask the Aadhaar numbers found on this page
	                System.out.println("🎭 Masking " + locations.size() + " instance(s) on page " + currentPage + "...");
	                BufferedImage maskedImage = maskAadhaarInImage(correctedImage, locations, maskingType);
	                maskedImages.add(maskedImage);

	                log.info("✅ Page {}: Masked {} Aadhaar number(s)", currentPage, locations.size());
	                System.out.println("✅ Page " + currentPage + " processing completed\n");

	            } else {
	                // No Aadhaar on this page - add original image
	                maskedImages.add(correctedImage);
	                System.out.println("ℹ️  No Aadhaar detected on page " + currentPage);
	                System.out.println("ℹ️  Page included without modifications\n");
	                log.info("Page {}: No Aadhaar numbers found", currentPage);
	            }

	        } catch (Exception e) {
	            log.error("❌ Error processing page {}: {}", currentPage, e.getMessage(), e);
	            System.out.println("❌ ERROR on page " + currentPage + ": " + e.getMessage());
	            
	            // Add original unprocessed page on error
	            BufferedImage errorImage = renderer.renderImageWithDPI(pageNum, 300);
	            maskedImages.add(errorImage);
	            System.out.println("⚠️  Page " + currentPage + " included without processing\n");
	        }
	    }

	    document.close();

	    // Print final summary
	    System.out.println("=".repeat(80));
	    System.out.println("📊 FINAL SUMMARY");
	    System.out.println("=".repeat(80));
	    System.out.println("   Total Pages: " + totalPages);
	    System.out.println("   Total Aadhaar Instances Found: " + allLocations.size());
	    System.out.println("   Status: " + (foundAnyAadhaar ? "✅ SUCCESS" : "⚠️  NO AADHAAR FOUND"));
	    System.out.println("=".repeat(80) + "\n");

	    if (foundAnyAadhaar) {
	        // Create output PDF with all pages
	        String outputFileName = "masked_" + System.currentTimeMillis() + ".pdf";
	        File outputFile = outputDir.resolve(outputFileName).toFile();

	        PDDocument newDoc = new PDDocument();

	        System.out.println("💾 Creating output PDF with " + maskedImages.size() + " page(s)...");
	        
	        for (int i = 0; i < maskedImages.size(); i++) {
	            BufferedImage maskedImage = maskedImages.get(i);

	            PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(
	                maskedImage.getWidth(), maskedImage.getHeight()));
	            newDoc.addPage(page);

	            File tempImg = File.createTempFile("masked_page_" + i, ".png");
	            ImageIO.write(maskedImage, "PNG", tempImg);
	            PDImageXObject pdImage = PDImageXObject.createFromFile(tempImg.getAbsolutePath(), newDoc);

	            try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, page)) {
	                contentStream.drawImage(pdImage, 0, 0, maskedImage.getWidth(), maskedImage.getHeight());
	            }

	            tempImg.delete();
	        }

	        newDoc.save(outputFile);
	        newDoc.close();

	        System.out.println("✅ Output PDF saved: " + outputFile.getName());
	        System.out.println("📦 File size: " + (outputFile.length() / 1024) + " KB");

	        String base64Image = convertImageToBase64(maskedImages.get(0), "PNG");

	        log.info("✅ Successfully processed {} page(s), masked {} Aadhaar instance(s)", 
	                 totalPages, allLocations.size());

	        return buildSuccessResult(originalFilename, outputFileName, contentType, 
	                                 extractionTime, startTime, allLocations, base64Image, maskingType);
	    }

	    // No Aadhaar found in entire PDF
	    System.out.println("⚠️  No Aadhaar numbers found in any page");
	    return buildErrorResult(originalFilename, contentType, extractionTime, 
	                           System.currentTimeMillis() - startTime, 
	                           "UID not found in any of the " + totalPages + " page(s)!!", 
	                           "AADHAAR_NOT_FOUND");
	}
	
	private IdDocumentExtractionResult processImageInternal(BufferedImage image, String originalFilename,
			String contentType, LocalDateTime extractionTime, long startTime, String extension, MaskingType maskingType)
			throws Exception {

		System.out.println("=== IMAGE PROCESSING DEBUG ===");
		System.out.println("Original image dimensions: " + image.getWidth() + "x" + image.getHeight());
		System.out.println("Masking type: " + maskingType);

		BufferedImage correctedImage = imageOrientationService.smartOrientationCorrection(image);
		System.out
				.println("Corrected image dimensions: " + correctedImage.getWidth() + "x" + correctedImage.getHeight());

		List<String> ocrLines = new ArrayList<>();
		try {
			String extractedText = "";
			try {
			    extractedText = tesseract.doOCR(correctedImage);
			    System.out.println("=== OCR EXTRACTED TEXT ===");
			    System.out.println(extractedText);
			    System.out.println("=== END OCR TEXT ===");

			    if (extractedText != null && !extractedText.trim().isEmpty()) {
			        ocrLines = Arrays.asList(extractedText.split("\\r?\\n"));
			    }
			} catch (Exception e) {
			    log.error("Error extracting text", e);
			}
		} catch (Exception e) {
			log.error("Error extracting text", e);
		}

		List<AadhaarLocationDto> locations = aadhaarDetectionService.findAadhaarNumbers(correctedImage, ocrLines);
		System.out.println("Found " + locations.size() + " Aadhaar number instances");

		if (!locations.isEmpty()) {
			// MODIFIED: Use Python or Java masking
			BufferedImage maskedImage = maskAadhaarInImage(correctedImage, locations, maskingType);
			BufferedImage outputImage = convertToSaveableFormat(maskedImage);

			String outputFileName = "masked_" + System.currentTimeMillis() + "." + extension;
			File outputFile = outputDir.resolve(outputFileName).toFile();
			outputFile.getParentFile().mkdirs();

			System.out.println("Saving to: " + outputFile.getAbsolutePath());

			String formatName = extension.equalsIgnoreCase("png") ? "PNG" : "JPEG";
			boolean saved = ImageIO.write(outputImage, formatName, outputFile);

			System.out.println("File saved successfully: " + saved);
			System.out.println("File size: " + outputFile.length() + " bytes");

			if (!saved || outputFile.length() == 0) {
				throw new IOException("Failed to save masked image to: " + outputFile.getAbsolutePath());
			}

			String base64Image = convertImageToBase64(outputImage, formatName);
			return buildSuccessResult(originalFilename, outputFileName, contentType, extractionTime, startTime,
					locations, base64Image, maskingType);
		}

		return buildErrorResult(originalFilename, contentType, extractionTime, System.currentTimeMillis() - startTime,
				"UID not found!!", "AADHAAR_NOT_FOUND");
	}

	private String convertImageToBase64(BufferedImage image, String formatName) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(image, formatName, baos);
		byte[] imageBytes = baos.toByteArray();
		return Base64.getEncoder().encodeToString(imageBytes);
	}

	private BufferedImage convertToSaveableFormat(BufferedImage image) {
		if (image.getType() == 0 || image.getType() == BufferedImage.TYPE_CUSTOM) {
			BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

			Graphics2D g = newImage.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
			g.drawImage(image, 0, 0, null);
			g.dispose();

			return newImage;
		}

		return image;
	}

	/**
	 * MODIFIED: Main masking method - routes to Python or Java implementation
	 */
	private BufferedImage maskAadhaarInImage(BufferedImage image, List<AadhaarLocationDto> locations,
			MaskingType maskingType) throws Exception {

		// Route to appropriate masking implementation
		if (usePythonMasking && maskingType != MaskingType.RIBBON) {
			log.info("Using Python-based text replacement for masking");
			try {
				return pythonTextReplacementService.replaceTextInImage(image, locations, maskingType);
			} catch (Exception e) {
				log.error("Python masking failed, falling back to Java implementation", e);
				// Fallback to Java implementation
				return maskAadhaarInImageJava(image, locations, maskingType);
			}
		} else {
			log.info("Using Java-based masking");
			return maskAadhaarInImageJava(image, locations, maskingType);
		}
	}

	/**
	 * Original Java-based masking implementation (renamed)
	 */
	private BufferedImage maskAadhaarInImageJava(BufferedImage image, List<AadhaarLocationDto> locations,
			MaskingType maskingType) {

		int imageType = (image.getType() == 0) ? BufferedImage.TYPE_INT_RGB : image.getType();
		BufferedImage maskedImage = new BufferedImage(image.getWidth(), image.getHeight(), imageType);

		Graphics2D g = maskedImage.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		g.drawImage(image, 0, 0, null);

		// Mask each Aadhaar number found
		for (AadhaarLocationDto loc : locations) {
			String aadhaarNumber = loc.getUid();
			String lastFourDigits = aadhaarNumber.substring(8);

			log.info("Masking Aadhaar: {} at position ({}, {})", aadhaarNumber, loc.getX(), loc.getY());

			if (maskingType == MaskingType.RIBBON) {
				maskWithRibbon(g, loc);
			} else {
				maskWithTextSecure(g, loc, maskingType, lastFourDigits, image);
			}
		}

		g.dispose();
		return maskedImage;
	}

	private void maskWithRibbon(Graphics2D g, AadhaarLocationDto loc) {
		g.setColor(Color.BLACK);
		g.fillRect(loc.getX() - 2, loc.getY() - 2, loc.getWidth() + 4, loc.getHeight() + 4);
	}

	private void maskWithTextSecure(Graphics2D g, AadhaarLocationDto loc, MaskingType maskingType,
			String lastFourDigits, BufferedImage image) {

		List<Rectangle> digitBoxes = loc.getDigitBoxes();
		if (digitBoxes == null || digitBoxes.size() != 3) {
			log.warn("Expected 3 digit groups, got {}", digitBoxes == null ? 0 : digitBoxes.size());
			maskWithRibbon(g, loc);
			return;
		}

		char maskChar = (maskingType == MaskingType.ASTERISK) ? '*' : 'X';

		// Process each group (4 digits)
		for (int groupIndex = 0; groupIndex < 3; groupIndex++) {
			Rectangle groupBox = digitBoxes.get(groupIndex);

			// Split group into 4 individual digit boxes
			List<Rectangle> individualDigitBoxes = splitGroupIntoDigits(groupBox,
					groupIndex == 2 ? lastFourDigits : null);

			for (int digitIndex = 0; digitIndex < 4; digitIndex++) {
				Rectangle digitBox = individualDigitBoxes.get(digitIndex);
				char actualDigit = loc.getUid().charAt(groupIndex * 4 + digitIndex);
				boolean shouldMask = groupIndex < 2; // Only mask first 8 digits

				if (shouldMask) {
					maskSingleDigit(g, image, digitBox, maskChar, actualDigit);
				}
			}
		}
	}
	
	private void maskSingleDigit(Graphics2D g, BufferedImage image, Rectangle digitBox, char maskChar, char actualDigit) {
	    int padding = 1;

	    // Sample background color from around the digit
	    Color bgColor = getAverageColor(image, digitBox.x - 5, digitBox.y - 5, digitBox.width + 10, digitBox.height + 10);

	    // Fill the digit area with sampled background
	    g.setColor(bgColor);
	    g.fillRect(digitBox.x - padding, digitBox.y - padding,
	               digitBox.width + padding * 2, digitBox.height + padding * 2);

	    // Find the perfect font size and style
	    Font bestFont = findFontForExactHeight(g, maskChar, digitBox.height, digitBox.width);
	    g.setFont(bestFont);

	    // Get exact metrics for centering
	    FontMetrics fm = g.getFontMetrics();
	    int ascent = fm.getAscent();
	    int descent = fm.getDescent();
	    int charWidth = fm.charWidth(maskChar);

	    // Calculate centered position
	    int baselineY = digitBox.y + (digitBox.height + ascent - descent) / 2;
	    int startX = digitBox.x + (digitBox.width - charWidth) / 2;

	    // Draw masked character
	    g.setColor(Color.BLACK);
	    g.drawString(String.valueOf(maskChar), startX, baselineY);

	    // Add subtle noise
	    addSubtleNoise(g, digitBox);
	}

	private List<Rectangle> splitGroupIntoDigits(Rectangle groupBox, String lastFour) {
		List<Rectangle> digitBoxes = new ArrayList<>();
		int totalWidth = groupBox.width;
		int digitWidth = totalWidth / 4;

		for (int i = 0; i < 4; i++) {
			int x = groupBox.x + i * digitWidth;
			int y = groupBox.y;
			int width = digitWidth;
			int height = groupBox.height;

			if (lastFour != null && i >= 0) {
				width = Math.max(1, width - 2);
			}

			digitBoxes.add(new Rectangle(x, y, width, height));
		}

		return digitBoxes;
	}

	private Color getAverageColor(BufferedImage img, int x, int y, int w, int h) {
		long r = 0, g = 0, b = 0;
		int count = 0;

		for (int i = x; i < x + w; i++) {
			for (int j = y; j < y + h; j++) {
				if (i < 0 || j < 0 || i >= img.getWidth() || j >= img.getHeight())
					continue;
				int rgb = img.getRGB(i, j);
				Color c = new Color(rgb);
				r += c.getRed();
				g += c.getGreen();
				b += c.getBlue();
				count++;
			}
		}
		if (count == 0)
			return Color.WHITE;
		return new Color((int) (r / count), (int) (g / count), (int) (b / count));
	}

	private Font findFontForExactHeight(Graphics2D g, char character, int targetHeight, int targetWidth) {
	    int minSize = 6;
	    int maxSize = Math.min(72, (int) (targetHeight * 1.5));

	    Font bestFont = null;
	    int bestDiff = Integer.MAX_VALUE;

	    for (int size = minSize; size <= maxSize; size++) {
	        Font testFont = getFontPlain(size);
	        FontMetrics fm = g.getFontMetrics(testFont);
	        int ascent = fm.getAscent();
	        int charWidth = fm.charWidth(character);

	        int diff = Math.abs(ascent - targetHeight);

	        if (charWidth <= targetWidth * 0.9 && diff < bestDiff) {
	            bestDiff = diff;
	            bestFont = testFont;
	        }

	        if (diff <= 2 && charWidth <= targetWidth * 0.9) {
	            break;
	        }
	    }

	    if (bestFont == null) {
	        bestFont = getFontPlain(targetHeight / 2);
	    }

	    return bestFont;
	}
	
	private void addSubtleNoise(Graphics2D g, Rectangle box) {
	    Random random = new Random(box.x ^ box.y);
	    int noiseDensity = 5;

	    for (int y = box.y; y < box.y + box.height; y += 2) {
	        for (int x = box.x; x < box.x + box.width; x += 2) {
	            if (random.nextInt(100) < noiseDensity) {
	                int grayValue = 200 + random.nextInt(30);
	                g.setColor(new Color(grayValue, grayValue, grayValue));
	                g.fillRect(x, y, 1, 1);
	            }
	        }
	    }
	}

	private Font getFontPlain(int fontSize) {
		String[] fontNames = { "Arial", "Helvetica", "SansSerif", "Dialog" };
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		String[] availableFonts = ge.getAvailableFontFamilyNames();
		Set<String> availableSet = new HashSet<>(Arrays.asList(availableFonts));

		for (String fontName : fontNames) {
			if (availableSet.contains(fontName)) {
				return new Font(fontName, Font.PLAIN, fontSize);
			}
		}

		return new Font(Font.DIALOG, Font.PLAIN, fontSize);
	}

	private String checkForAadhaarWithText(BufferedImage image) {
		try {
			String text = tesseract.doOCR(image);
			if (findTextPattern(text)) {
				return text;
			}
			return null;
		} catch (Exception e) {
			log.error("Error checking for Aadhaar", e);
			return null;
		}
	}

	private IdDocumentExtractionResult buildSuccessResult(String originalFilename, String outputFileName,
			String contentType, LocalDateTime extractionTime, long startTime, List<AadhaarLocationDto> locations,
			String base64Image, MaskingType maskingType) {

		IdDocumentExtractionResult result = new IdDocumentExtractionResult();
		result.setId(UUID.randomUUID().toString());
		result.setFileName(outputFileName);
		result.setFileType(contentType);
		result.setIdType("AADHAAR");

		IdDocumentExtractionResult.IdDocumentContent content = new IdDocumentExtractionResult.IdDocumentContent();
		content.setMaskedAadhaarNumbers(getMaskedNumbersDisplay(locations, maskingType));
		content.setAadhaarCount(locations.size());
		content.setOriginalFileName(originalFilename);
		content.setMaskedFileBase64(base64Image);
		content.setMaskedFilePath(outputDir.resolve(outputFileName).toString());
		content.setMaskingType(maskingType.toString());
		result.setContent(content);

		result.setExtractionTime(extractionTime);
		result.setExtractionMethod("OCR_TESSERACT");
		result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
		result.setSuccess(true);
		result.setErrorMessage(null);

		return result;
	}

	private String getMaskedNumbersDisplay(List<AadhaarLocationDto> locations, MaskingType maskingType) {
		return locations.stream().map(loc -> {
			String lastFour = loc.getUid().substring(8);
			if (maskingType == MaskingType.ASTERISK) {
				return "**** **** " + lastFour;
			} else if (maskingType == MaskingType.X) {
				return "XXXX XXXX " + lastFour;
			} else {
				return "████ ████ " + lastFour;
			}
		}).collect(Collectors.joining(", "));
	}

	private IdDocumentExtractionResult buildErrorResult(String originalFilename, String contentType,
			LocalDateTime extractionTime, long processingTime, String errorMessage, String errorCode) {
		IdDocumentExtractionResult result = new IdDocumentExtractionResult();
		result.setId(UUID.randomUUID().toString());
		result.setFileName(originalFilename);
		result.setFileType(contentType);
		result.setIdType("AADHAAR");
		result.setContent(null);
		result.setExtractionTime(extractionTime);
		result.setExtractionMethod("OCR_TESSERACT");
		result.setProcessingTimeMs(processingTime);
		result.setSuccess(false);
		result.setErrorMessage(errorMessage);
		result.setErrorCode(errorCode != null ? errorCode : "UNKNOWN_ERROR");
		return result;
	}

	private boolean findTextPattern(String text) {
		if (text.length() < 12)
			return false;
		Pattern pattern = Pattern.compile("\\d{12}");
		return pattern.matcher(text.replaceAll(" ", "")).find();
	}

	private String getFileExtension(String filename) {
		int lastDot = filename.lastIndexOf('.');
		return lastDot > 0 ? filename.substring(lastDot + 1) : "";
	}
}