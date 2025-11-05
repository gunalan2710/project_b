package ai.simplfin.doczbot.services.masking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.simplfin.doczbot.dto.AadhaarLocationDto;
import jakarta.annotation.PostConstruct;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PythonTextReplacementService {

    private static final Logger log = LoggerFactory.getLogger(PythonTextReplacementService.class);

    @Value("${opencv.native.lib.path:}")
    private String opencvPath;

    @Value("${opencv.required:false}")
    private boolean opencvRequired;

    private boolean opencvLoaded = false;

    @PostConstruct
    public void initOpenCV() {
        // Try method 1: System library (Docker with java.library.path)
        if (tryLoadSystemLibrary()) {
            opencvLoaded = true;
            return;
        }

        // Try method 2: Common Linux paths
        if (tryLoadLinuxPaths()) {
            opencvLoaded = true;
            return;
        }

        // Try method 3: Custom path from properties
        if (tryLoadCustomPath()) {
            opencvLoaded = true;
            return;
        }

        // Try method 4: nu.pattern.OpenCV (if available)
        if (tryLoadNuPattern()) {
            opencvLoaded = true;
            return;
        }

        // All methods failed
        String message = "OpenCV could not be loaded. Available library path: " + System.getProperty("java.library.path");
        
        if (opencvRequired) {
            log.error("❌ " + message);
            throw new RuntimeException(message);
        } else {
            log.warn("⚠️ " + message + " - Continuing without OpenCV");
        }
    }

    private boolean tryLoadSystemLibrary() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            log.info("✅ OpenCV loaded from system library: {}", Core.NATIVE_LIBRARY_NAME);
            log.info("✅ OpenCV version: {}", Core.VERSION);
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.debug("⚠️ Could not load OpenCV from system library: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryLoadLinuxPaths() {
        String[] possiblePaths = {
            "/usr/lib/jni/libopencv_java490.so",
            "/usr/lib/jni/libopencv_java455.so",
            "/usr/lib/jni/libopencv_java.so",
            "/usr/lib/x86_64-linux-gnu/libopencv_java490.so",
            "/usr/lib/x86_64-linux-gnu/libopencv_java.so",
            "/usr/lib/aarch64-linux-gnu/libopencv_java490.so",
            "/usr/lib/aarch64-linux-gnu/libopencv_java.so"
        };

        for (String path : possiblePaths) {
            try {
                System.load(path);
                log.info("✅ OpenCV loaded from path: {}", path);
                log.info("✅ OpenCV version: {}", Core.VERSION);
                return true;
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                log.debug("⚠️ Could not load OpenCV from: {}", path);
            }
        }
        return false;
    }

    private boolean tryLoadCustomPath() {
        if (opencvPath == null || opencvPath.isEmpty()) {
            return false;
        }

        try {
            System.load(opencvPath);
            log.info("✅ OpenCV loaded from custom path: {}", opencvPath);
            log.info("✅ OpenCV version: {}", Core.VERSION);
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.debug("⚠️ Could not load OpenCV from custom path: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryLoadNuPattern() {
        try {
            Class<?> openCVClass = Class.forName("nu.pattern.OpenCV");
            java.lang.reflect.Method loadLocalMethod = openCVClass.getMethod("loadLocally");
            loadLocalMethod.invoke(null);
            log.info("✅ OpenCV loaded via nu.pattern.OpenCV");
            log.info("✅ OpenCV version: {}", Core.VERSION);
            return true;
        } catch (Exception e) {
            log.debug("⚠️ nu.pattern.OpenCV not available: {}", e.getMessage());
            return false;
        }
    }

    public boolean isOpencvLoaded() {
        return opencvLoaded;
    }


    private void loadSystemLibrary() {
        try {
            // Try loading from system (works on Linux with installed OpenCV)
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            log.info("✅ OpenCV loaded from system library: {}", Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            log.error("❌ Could not load OpenCV from system library: {}", e.getMessage());
            throw new RuntimeException("OpenCV not found in system libraries. Please ensure OpenCV is installed.", e);
        }
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================
    
    public static class Replacement {
        public int x, y;
        public String newText;
        public int fontSize;

        public Replacement(int x, int y, String newText, int fontSize) {
            this.x = x;
            this.y = y;
            this.newText = newText;
            this.fontSize = fontSize;
        }
    }

    private static class CharacterPosition {
        Rectangle bounds;

        CharacterPosition(int x, int y, int width, int height) {
            this.bounds = new Rectangle(x, y, width, height);
        }
    }

    // ============================================================================
    // MAIN ENTRY POINT - Enhanced precision masking
    // ============================================================================

    public BufferedImage replaceTextInImage(BufferedImage image, List<AadhaarLocationDto> locations,
            AadhaarMaskingService.MaskingType maskingType) throws Exception {
        return replaceTextInImagePrecise(image, locations, maskingType);
    }

    /**
     * ENHANCED: Precise character-by-character masking with proper sizing
     */
    public BufferedImage replaceTextInImagePrecise(BufferedImage image, List<AadhaarLocationDto> locations,
            AadhaarMaskingService.MaskingType maskingType) throws Exception {

        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }
        
        if (locations == null || locations.isEmpty()) {
            log.warn("No locations provided for masking");
            return image;
        }

        log.info("Starting PRECISE character-by-character masking for {} location(s)", locations.size());

        Mat matImage = bufferedImageToMat(image);
        
        if (matImage.empty()) {
            throw new RuntimeException("Failed to convert image to Mat format");
        }

        try {
            for (AadhaarLocationDto loc : locations) {
                if (loc == null || loc.getUid() == null) {
                    log.warn("Skipping null location or UID");
                    continue;
                }
                
                String aadhaarNumber = loc.getUid();
                char maskChar = (maskingType == AadhaarMaskingService.MaskingType.X) ? 'X' : '*';

                List<Rectangle> digitBoxes = loc.getDigitBoxes();
                if (digitBoxes == null || digitBoxes.size() != 3) {
                    log.warn("Invalid digit boxes count: {}, expected 3 groups",
                            digitBoxes == null ? 0 : digitBoxes.size());
                    continue;
                }

                log.info("Processing Aadhaar: {}", aadhaarNumber);

                // Process only the first 2 groups (first 8 digits)
                for (int groupIdx = 0; groupIdx < 2; groupIdx++) {
                    Rectangle groupBox = digitBoxes.get(groupIdx);
                    
                    if (groupBox == null || groupBox.width <= 0 || groupBox.height <= 0) {
                        log.warn("Invalid group box at index {}", groupIdx);
                        continue;
                    }

                    log.debug("Group {} - Box position: x={}, y={}, w={}, h={}", groupIdx, groupBox.x, groupBox.y,
                            groupBox.width, groupBox.height);

                    // Get individual digit rectangles using contour detection
                    List<Rectangle> individualDigits = getIndividualDigitRectangles(groupBox, matImage);

                    if (individualDigits.size() != 4) {
                        log.warn("Group {} - Expected 4 digits, got {}. Using fallback division.", groupIdx,
                                individualDigits.size());
                        individualDigits = getFallbackDigitRectangles(groupBox);
                    }

                    // Process each digit in this group
                    for (int digitIdx = 0; digitIdx < Math.min(4, individualDigits.size()); digitIdx++) {
                        char targetChar = maskChar;
                        Rectangle digitRect = individualDigits.get(digitIdx);

                        log.debug("  Digit {}: char='{}', rect=[{},{},{},{}]", digitIdx, targetChar, digitRect.x,
                                digitRect.y, digitRect.width, digitRect.height);

                        // Process this single character
                        processCharacterEnhanced(matImage, digitRect, targetChar, groupBox.height);
                    }
                }
                // Group 3 (last 4 digits) is intentionally skipped - left unchanged
            }

            BufferedImage result = matToBufferedImage(matImage);
            log.info("Masking completed successfully");
            return result;
            
        } finally {
            // Always release Mat resources
            matImage.release();
        }
    }

    // ============================================================================
    // DIGIT RECTANGLE DETECTION
    // ============================================================================

    /**
     * Get individual digit rectangles using contour detection
     */
    private List<Rectangle> getIndividualDigitRectangles(Rectangle groupBox, Mat matImage) {
        List<Rectangle> digitRects = new ArrayList<>();

        Mat roi = null;
        Mat gray = null;
        Mat binary = null;
        Mat hierarchy = null;
        
        try {
            // Extract the group region with padding
            int padding = 2;
            int x1 = Math.max(0, groupBox.x - padding);
            int y1 = Math.max(0, groupBox.y - padding);
            int x2 = Math.min(matImage.cols(), groupBox.x + groupBox.width + padding);
            int y2 = Math.min(matImage.rows(), groupBox.y + groupBox.height + padding);

            if (x2 <= x1 || y2 <= y1) {
                log.warn("Invalid ROI dimensions");
                return digitRects;
            }

            roi = matImage.submat(new Rect(x1, y1, x2 - x1, y2 - y1));

            // Convert to grayscale
            gray = new Mat();
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY);

            // Apply adaptive threshold for better digit separation
            binary = new Mat();
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
                    Imgproc.THRESH_BINARY_INV, 11, 2);

            // Find contours
            List<MatOfPoint> contours = new ArrayList<>();
            hierarchy = new Mat();
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Get bounding boxes and filter
            List<Rect> boxes = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect rect = Imgproc.boundingRect(contour);

                // Filter: must be reasonable size for a digit
                int minWidth = groupBox.width / 8;
                int minHeight = groupBox.height / 2;

                if (rect.width >= minWidth && rect.height >= minHeight) {
                    boxes.add(rect);
                }
            }

            // Sort by x position (left to right)
            boxes.sort(Comparator.comparingInt(r -> r.x));

            // Take first 4 boxes (should be our 4 digits)
            for (int i = 0; i < Math.min(4, boxes.size()); i++) {
                Rect box = boxes.get(i);
                digitRects.add(new Rectangle(x1 + box.x, y1 + box.y, box.width, box.height));
            }

        } catch (Exception e) {
            log.warn("Contour detection failed: {}", e.getMessage());
        } finally {
            // Clean up Mats
            if (roi != null) roi.release();
            if (gray != null) gray.release();
            if (binary != null) binary.release();
            if (hierarchy != null) hierarchy.release();
        }

        return digitRects;
    }

    /**
     * Fallback: Equal division if contour detection fails
     */
    private List<Rectangle> getFallbackDigitRectangles(Rectangle groupBox) {
        List<Rectangle> digitRects = new ArrayList<>();
        int digitWidth = groupBox.width / 4;

        for (int i = 0; i < 4; i++) {
            digitRects.add(new Rectangle(
                groupBox.x + (i * digitWidth), 
                groupBox.y, 
                digitWidth, 
                groupBox.height
            ));
        }

        return digitRects;
    }

    // ============================================================================
    // CHARACTER PROCESSING - ENHANCED VERSION
    // ============================================================================

    /**
     * Process single character with enhanced background matching and sizing
     */
    private void processCharacterEnhanced(Mat matImage, Rectangle charRect, char character, int originalGroupHeight) {
        Mat mask = null;
        Mat inpainted = null;
        Mat newMat = null;
        
        try {
            // Step 1: Sample background color
            Scalar bgColor = sampleBackgroundAdvanced(matImage, charRect);

            // Step 2: Create smooth mask
            mask = createFeatheredMask(matImage, charRect);

            // Step 3: Inpaint the area
            inpainted = new Mat();
            Photo.inpaint(matImage, mask, inpainted, 3.0, Photo.INPAINT_TELEA);

            // Step 4: Blend inpainted area
            blendInpaintedArea(matImage, inpainted, mask);

            // Step 5: Convert to BufferedImage for text drawing
            BufferedImage bufferedImg = matToBufferedImage(matImage);

            // Step 6: Draw character with perfect sizing
            drawCharacterPerfect(bufferedImg, charRect, character, bgColor, originalGroupHeight);

            // Step 7: Convert back to Mat
            newMat = bufferedImageToMat(bufferedImg);
            newMat.copyTo(matImage);
            
        } catch (Exception e) {
            log.error("Error processing character: {}", e.getMessage(), e);
        } finally {
            // Cleanup
            if (mask != null) mask.release();
            if (inpainted != null) inpainted.release();
            if (newMat != null) newMat.release();
        }
    }

    /**
     * FIXED: Draw character with correct size and perfect positioning
     */
    private void drawCharacterPerfect(BufferedImage img, Rectangle rect, char character, Scalar bgColor,
            int originalGroupHeight) {
        Graphics2D g2d = img.createGraphics();

        try {
            // High quality rendering
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // CRITICAL FIX: Use the ORIGINAL group height for font size
            // This ensures ALL characters (masked and unmasked) are the same size
            int baseFontSize = (int) (originalGroupHeight * 1.0); // 100% of original height

            // Find best font size that fits both height and width
            Font bestFont = null;
            FontMetrics bestFm = null;
            int bestCharWidth = 0;

            for (int size = baseFontSize; size >= baseFontSize - 8; size--) {
                Font testFont = new Font("Arial", Font.BOLD, size);
                g2d.setFont(testFont);
                FontMetrics fm = g2d.getFontMetrics();
                int charWidth = fm.charWidth(character);
                int charHeight = fm.getHeight();

                // Check if it fits in the rectangle
                if (charWidth <= rect.width && charHeight <= rect.height + 4) {
                    bestFont = testFont;
                    bestFm = fm;
                    bestCharWidth = charWidth;
                    break;
                }
            }

            // Fallback if no good size found
            if (bestFont == null) {
                bestFont = new Font("Arial", Font.BOLD, Math.max(8, baseFontSize - 10));
                g2d.setFont(bestFont);
                bestFm = g2d.getFontMetrics();
                bestCharWidth = bestFm.charWidth(character);
            }

            // FIXED: Fill background first with sampled color
            g2d.setColor(new Color(
                (int) Math.min(255, Math.max(0, bgColor.val[2])),
                (int) Math.min(255, Math.max(0, bgColor.val[1])),
                (int) Math.min(255, Math.max(0, bgColor.val[0]))
            ));
            g2d.fillRect(rect.x, rect.y, rect.width, rect.height);

            // Calculate perfect positioning
            int ascent = bestFm.getAscent();
            int descent = bestFm.getDescent();

            // Center horizontally
            int textX = rect.x + (rect.width - bestCharWidth) / 2;

            // FIXED: Better vertical centering
            // Place text so it's vertically centered in the rectangle
            int textY = rect.y + ((rect.height + ascent - descent) / 2);

            // Draw the character in black
            g2d.setColor(Color.BLACK);
            g2d.drawString(String.valueOf(character), textX, textY);
            
        } finally {
            g2d.dispose();
        }
    }

    // ============================================================================
    // BACKGROUND SAMPLING AND INPAINTING
    // ============================================================================

    /**
     * Advanced background sampling with median calculation
     */
    private Scalar sampleBackgroundAdvanced(Mat img, Rectangle charRect) {
        List<Scalar> samples = new ArrayList<>();

        // Sample points around all 4 sides
        int[][] sampleOffsets = {
            // Left side
            {-4, 0}, {-4, charRect.height / 4}, {-4, charRect.height / 2}, {-4, 3 * charRect.height / 4},
            // Right side
            {charRect.width + 4, 0}, {charRect.width + 4, charRect.height / 4},
            {charRect.width + 4, charRect.height / 2}, {charRect.width + 4, 3 * charRect.height / 4},
            // Top
            {charRect.width / 4, -4}, {charRect.width / 2, -4}, {3 * charRect.width / 4, -4},
            // Bottom
            {charRect.width / 4, charRect.height + 4}, {charRect.width / 2, charRect.height + 4},
            {3 * charRect.width / 4, charRect.height + 4}
        };

        for (int[] offset : sampleOffsets) {
            int sampleX = charRect.x + offset[0];
            int sampleY = charRect.y + offset[1];

            if (sampleX >= 0 && sampleX < img.cols() && sampleY >= 0 && sampleY < img.rows()) {
                double[] pixel = img.get(sampleY, sampleX);
                if (pixel != null && pixel.length >= 3) {
                    samples.add(new Scalar(pixel[0], pixel[1], pixel[2]));
                }
            }
        }

        if (samples.isEmpty()) {
            return new Scalar(255, 255, 255);
        }

        // Calculate median (more robust than mean)
        return calculateMedianColor(samples);
    }

    /**
     * Calculate median color from samples
     */
    private Scalar calculateMedianColor(List<Scalar> samples) {
        if (samples.isEmpty()) {
            return new Scalar(255, 255, 255);
        }

        List<Double> blues = samples.stream().map(s -> s.val[0]).sorted().collect(Collectors.toList());
        List<Double> greens = samples.stream().map(s -> s.val[1]).sorted().collect(Collectors.toList());
        List<Double> reds = samples.stream().map(s -> s.val[2]).sorted().collect(Collectors.toList());

        int mid = samples.size() / 2;
        return new Scalar(blues.get(mid), greens.get(mid), reds.get(mid));
    }

    /**
     * Create feathered mask for smooth blending
     */
    private Mat createFeatheredMask(Mat img, Rectangle rect) {
        Mat mask = Mat.zeros(img.size(), CvType.CV_8UC1);
        Mat blurred = null;
        
        try {
            // Ensure valid coordinates
            int x1 = Math.max(0, rect.x);
            int y1 = Math.max(0, rect.y);
            int x2 = Math.min(img.cols(), rect.x + rect.width);
            int y2 = Math.min(img.rows(), rect.y + rect.height);

            if (x2 > x1 && y2 > y1) {
                Mat roi = mask.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
                roi.setTo(new Scalar(255));
            }

            // Apply Gaussian blur for feathering
            blurred = new Mat();
            Imgproc.GaussianBlur(mask, blurred, new Size(5, 5), 1.5);
            
            return blurred;
            
        } finally {
            mask.release();
        }
    }

    /**
     * Blend inpainted area smoothly into original
     */
    private void blendInpaintedArea(Mat original, Mat inpainted, Mat mask) {
        Mat normalized = null;
        
        try {
            normalized = new Mat();
            mask.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);

            for (int y = 0; y < original.rows(); y++) {
                for (int x = 0; x < original.cols(); x++) {
                    double alpha = normalized.get(y, x)[0];
                    if (alpha > 0.01) {
                        double[] origPixel = original.get(y, x);
                        double[] inpaintPixel = inpainted.get(y, x);

                        double[] blended = new double[3];
                        for (int c = 0; c < 3; c++) {
                            blended[c] = origPixel[c] * (1 - alpha) + inpaintPixel[c] * alpha;
                        }
                        original.put(y, x, blended);
                    }
                }
            }
        } finally {
            if (normalized != null) normalized.release();
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Convert OpenCV Mat to BufferedImage
     */
    private static BufferedImage matToBufferedImage(Mat mat) {
        int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage bufferedImage = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return bufferedImage;
    }

    /**
     * Convert BufferedImage to OpenCV Mat
     */
    private static Mat bufferedImageToMat(BufferedImage bufferedImage) {
        // Ensure we have 3-channel BGR image
        BufferedImage convertedImage = bufferedImage;
        if (bufferedImage.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            convertedImage = new BufferedImage(
                bufferedImage.getWidth(), 
                bufferedImage.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR
            );
            Graphics2D g = convertedImage.createGraphics();
            g.drawImage(bufferedImage, 0, 0, null);
            g.dispose();
        }

        Mat mat = new Mat(convertedImage.getHeight(), convertedImage.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) convertedImage.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }
}