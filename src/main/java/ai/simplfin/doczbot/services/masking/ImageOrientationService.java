package ai.simplfin.doczbot.services.masking;


import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.List;

import org.opencv.core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Word;

@Service
@Slf4j
public class ImageOrientationService {

    private static final Logger log = LoggerFactory.getLogger(ImageOrientationService.class);
    private final ITesseract tesseract;

    public ImageOrientationService(ITesseract tesseract) {
        this.tesseract = tesseract;
    }

    /**
     * Detects orientation and returns correctly oriented image
     * This method tries all 4 rotations and picks the best one
     */
    public BufferedImage autoCorrectOrientation(BufferedImage image) throws Exception {
        log.debug("Starting orientation detection...");
        return findBestRotation(image);
    }

    /**
     * Try all 4 rotations and select the one with best OCR results
     */
    private BufferedImage findBestRotation(BufferedImage image) throws Exception {
        int[] angles = {0, 90, 180, 270};
        int bestAngle = 0;
        double bestConfidence = 0;
        BufferedImage bestImage = image;
        
        for (int angle : angles) {
            BufferedImage rotated = rotateImage(image, angle);
            double confidence = calculateOCRConfidence(rotated);
            
            log.debug("Rotation {}° confidence: {}", angle, confidence);
            
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestAngle = angle;
                bestImage = rotated;
            }
        }
        
        log.info("Best rotation detected: {}° (confidence: {})", bestAngle, bestConfidence);
        
        return bestImage;
    }

    /**
     * Calculate OCR confidence by checking for expected patterns
     */
    private double calculateOCRConfidence(BufferedImage image) {
        try {
            String text = tesseract.doOCR(image);
            double score = 0.0;
            
            if (text == null || text.trim().isEmpty()) {
                return 0.0;
            }
            
            // Check for common Aadhaar card keywords
            String upperText = text.toUpperCase();
            
            // High-value keywords
            if (upperText.contains("GOVERNMENT")) score += 25;
            if (upperText.contains("INDIA")) score += 20;
            if (upperText.contains("AADHAAR") || upperText.contains("आधार")) score += 25;
            
            // Medium-value keywords
            if (upperText.contains("DOB") || upperText.contains("DATE OF BIRTH") || 
                upperText.contains("பிறந்த நாள்")) score += 15;
            if (upperText.contains("MALE") || upperText.contains("FEMALE") || 
                upperText.contains("ஆண்") || upperText.contains("பெண்")) score += 15;
            if (upperText.contains("VID")) score += 10;
            if (upperText.contains("ISSUE DATE")) score += 10;
            
            // Check for 12-digit number pattern (Aadhaar format: XXXX XXXX XXXX)
            String digitsOnly = text.replaceAll("\\D", "");
            if (digitsOnly.length() >= 12) {
                score += 20;
                // Bonus if we find the exact pattern with spaces
                if (text.matches(".*\\d{4}\\s+\\d{4}\\s+\\d{4}.*")) {
                    score += 10;
                }
            }
            
            // Check word count (well-oriented images have more readable words)
            List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            if (words != null) {
                int validWordCount = (int) words.stream()
                    .filter(w -> w.getText() != null && w.getText().length() > 2)
                    .count();
                score += Math.min(validWordCount * 2, 30); // Cap at 30 points
            }
            
            return score;
        } catch (Exception e) {
            log.error("Error calculating OCR confidence: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Rotate image by specified angle (0, 90, 180, 270)
     */
    private BufferedImage rotateImage(BufferedImage image, int angle) {
        if (angle == 0) {
            return image;
        }
        
        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Calculate new dimensions after rotation
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);
        
        // Create rotated image with white background
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, 
            image.getType() == 0 ? BufferedImage.TYPE_INT_RGB : image.getType());
        
        // Fill with white background
        java.awt.Graphics2D g2d = rotated.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);
        
        // Create transform
        AffineTransform transform = new AffineTransform();
        transform.translate((newWidth - width) / 2.0, (newHeight - height) / 2.0);
        transform.rotate(radians, width / 2.0, height / 2.0);
        
        // Apply rotation
        AffineTransformOp op = new AffineTransformOp(transform, 
            AffineTransformOp.TYPE_BILINEAR);
        op.filter(image, rotated);
        
        g2d.dispose();
        
        return rotated;
    }

    /**
     * Quick check if image likely needs rotation
     * Returns true if confidence is very low (suggesting wrong orientation)
     */
    public boolean needsRotation(BufferedImage image) {
        try {
            double confidence = calculateOCRConfidence(image);
            return confidence < 30.0; // Threshold for "probably wrong orientation"
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Optimized version: Only check if rotation is needed first
     */
    public BufferedImage smartOrientationCorrection(BufferedImage image) throws Exception {
        log.debug("Starting smart orientation correction");
        log.debug("Input dimensions: {}x{}", image.getWidth(), image.getHeight());
        
        // First check if current orientation is already good
        double currentConfidence = calculateOCRConfidence(image);
        log.debug("Current orientation confidence: {}", currentConfidence);
        
        // If confidence is high enough, don't rotate
        if (currentConfidence >= 60.0) {
            log.info("Image orientation appears correct, skipping rotation");
            return image;
        }
        
        // Otherwise, find best rotation
        log.info("Low confidence detected, checking all rotations...");
        BufferedImage result = findBestRotation(image);
        log.debug("Output dimensions: {}x{}", result.getWidth(), result.getHeight());
        return result;
    }
}