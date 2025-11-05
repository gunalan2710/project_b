package ai.simplfin.doczbot.services.masking;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ai.simplfin.doczbot.dto.AadhaarLocationDto;
import ai.simplfin.doczbot.dto.NumWordDto;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

@Service
@Slf4j
public class AadhaarDetectionService {

    private final Tesseract tesseract;

    private static final Set<String> SUSPICIOUS_KEYWORDS = Set.of(
        "NAME", "FATHER", "MOTHER", "S/O", "D/O", "W/O", "H/O",
        "DOB", "DATE", "BIRTH", "YEAR", "AGE",
        "ADDRESS", "HOUSE", "STREET", "CITY", "PIN", "POST",
        "MOBILE", "PHONE", "CELL", "EMAIL", "GMAIL",
        "GENDER", "MALE", "FEMALE", "TRANSGENDER",
        "A/C", "ACCOUNT", "BANK", "IFSC", "CARD",
        "REF", "REFERENCE", "ID", "PASSPORT", "DL", "LICENSE",
        "FORM", "APPLICATION", "SIGNATURE"
    );

    public AadhaarDetectionService(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    /**
     * FIXED: Detect Aadhaar numbers even when VID is present on same page
     * Replace your entire findAadhaarNumbers() method with this version
     */
    public List<AadhaarLocationDto> findAadhaarNumbers(BufferedImage image, List<String> ocrLines) throws Exception {
        List<AadhaarLocationDto> locations = new ArrayList<>();

        // ✅ STEP 0: Strict Aadhaar format filter from OCR lines
        String aadhaarRegex = "^[0-9]{4}\\s[0-9]{4}\\s[0-9]{4}$";
        List<String> aadhaarCandidates = ocrLines.stream()
                .map(String::trim)
                .filter(line -> line.matches(aadhaarRegex))
                .distinct()
                .collect(Collectors.toList());

        System.out.println("\n=== STRICT OCR LINE FILTER ===");
        System.out.println("Total OCR lines: " + ocrLines.size());
        System.out.println("Valid Aadhaar formatted lines: " + aadhaarCandidates);

        if (!aadhaarCandidates.isEmpty()) {
            Set<String> validAadhaars = new HashSet<>();

            for (String line : aadhaarCandidates) {
                String aadhaarDigits = line.replaceAll("\\s+", "");
                if (isValidAadhaarFormat(aadhaarDigits)) {
                    validAadhaars.add(aadhaarDigits);
                    System.out.println("✅ Strict Aadhaar match found: " + line);
                } else {
                    System.out.println("❌ Invalid Aadhaar (checksum fail): " + line);
                }
            }

            // If we got valid Aadhaar numbers, find them in image directly
            if (!validAadhaars.isEmpty()) {
                List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
                System.out.println("Scanning " + words.size() + " words for Aadhaar placement...");

                for (String aadhaarNumber : validAadhaars) {
                    List<NumWordDto> fourDigitGroups = extractFourDigitGroups(words);
                    List<AadhaarLocationDto> instances = findAllInstancesOfAadhaar(
                        aadhaarNumber, fourDigitGroups, words, 35, 250
                    );
                    System.out.println("Found " + instances.size() + " instance(s) for " + aadhaarNumber);
                    locations.addAll(instances);
                }

                System.out.println("\n=== STRICT DETECTION COMPLETE: " + locations.size() + " total Aadhaar instance(s) ===");
                return locations;
            }
        }

        // ⚠️ STEP 1: Fallback to existing advanced logic (if strict format not found)
        System.out.println("⚠️ No strict Aadhaar format found in OCR lines. Falling back to advanced detection.");

        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        if (words == null || words.isEmpty()) {
            return locations;
        }

        System.out.println("\n=== STARTING AADHAAR DETECTION (MULTI-INSTANCE MODE) ===");
        System.out.println("Total words extracted: " + words.size());

        words.sort(
            Comparator.comparingInt((Word w) -> w.getBoundingBox().y)
                .thenComparingInt(w -> w.getBoundingBox().x)
        );

        final int Y_TOLERANCE = 35;
        final int X_GAP_MAX = 250;

        List<NumWordDto> numWords = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            NumWordDto nw = new NumWordDto(words.get(i), i);
            if (!nw.digits.isEmpty()) {
                numWords.add(nw);
            }
        }

        System.out.println("Total numeric words found: " + numWords.size());

        List<NumWordDto> fourDigitGroups = numWords.stream()
            .filter(NumWordDto::isFourDigits)
            .collect(Collectors.toList());

        System.out.println("Found " + fourDigitGroups.size() + " four-digit groups");

            // STEP 1: Find all unique valid Aadhaar numbers (ignoring VID patterns)
            Set<String> validAadhaarNumbers = new HashSet<>();

            for (int i = 0; i < fourDigitGroups.size() - 2; i++) {
                for (int j = i + 1; j < fourDigitGroups.size() - 1; j++) {
                    for (int k = j + 1; k < fourDigitGroups.size(); k++) {
                        NumWordDto g1 = fourDigitGroups.get(i);
                        NumWordDto g2 = fourDigitGroups.get(j);
                        NumWordDto g3 = fourDigitGroups.get(k);

                        // Check if on same line
                        int minY = Math.min(Math.min(g1.rect.y, g2.rect.y), g3.rect.y);
                        int maxY = Math.max(Math.max(g1.rect.y, g2.rect.y), g3.rect.y);
                        if (maxY - minY > Y_TOLERANCE) {
                            continue;
                        }

                        // Sort by X position
                        List<NumWordDto> sortedByX = new ArrayList<>(Arrays.asList(g1, g2, g3));
                        sortedByX.sort(Comparator.comparingInt(nw -> nw.rect.x));
                        
                        NumWordDto left = sortedByX.get(0);
                        NumWordDto middle = sortedByX.get(1);
                        NumWordDto right = sortedByX.get(2);

                        // Strict format validation
                        if (!isPureDigits(left.raw, 4) || 
                            !isPureDigits(middle.raw, 4) || 
                            !isPureDigits(right.raw, 4)) {
                            continue;
                        }

                        // Check gaps
                        int gap1 = middle.rect.x - (left.rect.x + left.rect.width);
                        int gap2 = right.rect.x - (middle.rect.x + middle.rect.width);
                        
                        if (gap1 < -10 || gap2 < -10 || gap1 > X_GAP_MAX || gap2 > X_GAP_MAX) {
                            continue;
                        }

                        // CRITICAL FIX: Check for 4th group (VID pattern) - but don't reject the entire page
//                        if (hasFourthGroupInSequence(fourDigitGroups, g1, g2, g3, left, right, Y_TOLERANCE)) {
//                            System.out.println("      ⚠️ This specific pattern has 4th group (VID) - skipping this combination");
//                            continue; // Skip THIS combination only, not all combinations
//                        }

                        // Try both reading orders
                        String leftToRight = left.digits + middle.digits + right.digits;
                        String rightToLeft = right.digits + middle.digits + left.digits;
                        
                        String validAadhaar = null;
                        
                        if (isValidAadhaarFormat(leftToRight)) {
                            validAadhaar = leftToRight;
                        } else if (isValidAadhaarFormat(rightToLeft)) {
                            validAadhaar = rightToLeft;
                        }
                        
                        if (validAadhaar == null) {
                            continue;
                        }

                        // NEW: Text-based validation - checks if this is Aadhaar or VID
                        if (!isValidAadhaarInTextContext(words, left, middle, right, validAadhaar)) {
                            System.out.println("      Rejected by text context validation");
                            continue;
                        }

                        // Skip VID keyword check
                        if (isNearVIDKeyword(words, left.index, left.rect.y, Y_TOLERANCE)) {
                            System.out.println("      ⚠️ Near VID keyword - skipping");
                            continue;
                        }

                        // Calculate bounding rectangle
                        int minX = left.rect.x;
                        int maxX = right.rect.x + right.rect.width;
                        int maxYBottom = Math.max(
                            Math.max(left.rect.y + left.rect.height, middle.rect.y + middle.rect.height),
                            right.rect.y + right.rect.height
                        );
                        Rectangle candidateRect = new Rectangle(minX, minY, maxX - minX, maxYBottom - minY);

                        // Check suspicious context
                        if (isNearSuspiciousContext(words, candidateRect, Y_TOLERANCE, validAadhaar)) {
                            System.out.println("      ⚠️ Near suspicious context - skipping");
                            continue;
                        }

                        // Add to unique set
                        validAadhaarNumbers.add(validAadhaar);
                        System.out.println("      ✅ Valid Aadhaar identified: " + 
                            validAadhaar.substring(0,4) + " " + 
                            validAadhaar.substring(4,8) + " " + 
                            validAadhaar.substring(8,12));
                    }
                }
            }

            System.out.println("✅ Found " + validAadhaarNumbers.size() + " unique valid Aadhaar number(s): " + validAadhaarNumbers);

            // STEP 2: For each unique Aadhaar, find ALL instances in the image
            for (String aadhaarNumber : validAadhaarNumbers) {
                System.out.println("\n🔍 Searching for all instances of: " + 
                    aadhaarNumber.substring(0, 4) + " " + 
                    aadhaarNumber.substring(4, 8) + " " + 
                    aadhaarNumber.substring(8, 12));
                
                List<AadhaarLocationDto> instances = findAllInstancesOfAadhaar(
                    aadhaarNumber, fourDigitGroups, words, Y_TOLERANCE, X_GAP_MAX
                );
                
                System.out.println("   Found " + instances.size() + " instance(s) of this number");
                locations.addAll(instances);
            }

            System.out.println("\n=== DETECTION COMPLETE: " + locations.size() + " total Aadhaar instance(s) found ===\n");
            return locations;
        }

    /**
     * Extracts all numeric OCR words that are exactly 4 digits long.
     */
    private List<NumWordDto> extractFourDigitGroups(List<Word> words) {
        if (words == null || words.isEmpty()) {
            return Collections.emptyList();
        }

        List<NumWordDto> numWords = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            NumWordDto nw = new NumWordDto(words.get(i), i);
            if (!nw.digits.isEmpty() && nw.isFourDigits()) {
                numWords.add(nw);
            }
        }
        return numWords;
    }

    
    /**
     * ENHANCED: Better validation to distinguish Aadhaar from VID on same line
     */
    private boolean isValidAadhaarInTextContext(List<Word> allWords, 
                                                NumWordDto left, 
                                                NumWordDto middle, 
                                                NumWordDto right,
                                                String aadhaarNumber) {
        
        // Get all words on the same line
        int centerY = (left.rect.y + middle.rect.y + right.rect.y) / 3;
        int yTolerance = 15;
        
        List<Word> sameLine = new ArrayList<>();
        for (Word word : allWords) {
            if (Math.abs(word.getBoundingBox().y - centerY) <= yTolerance) {
                sameLine.add(word);
            }
        }
        
        sameLine.sort(Comparator.comparingInt(w -> w.getBoundingBox().x));
        
        // Build the text line
        StringBuilder lineText = new StringBuilder();
        for (Word word : sameLine) {
            lineText.append(word.getText()).append(" ");
        }
        
        String line = lineText.toString();
        System.out.println("      Text on same line: '" + line.trim() + "'");
        
        // Extract all digit sequences
        List<String> digitSequences = extractDigitSequences(line);
        System.out.println("      Digit sequences found: " + digitSequences);
        
        // Count four-digit groups on this specific line
        long fourDigitGroupCount = sameLine.stream()
            .map(w -> w.getText().replaceAll("\\D", ""))
            .filter(s -> s.length() == 4)
            .count();
        
        System.out.println("      Four-digit groups on line: " + fourDigitGroupCount);
        
        // CRITICAL CHECK: If there are 4 four-digit groups, it's VID
        if (fourDigitGroupCount >= 4) {
            System.out.println("      ⚠️ Found " + fourDigitGroupCount + " four-digit groups - likely VID pattern");
            return false;
        }
        
        // Check for 16-digit sequence (VID)
        for (String seq : digitSequences) {
            if (seq.length() == 16 || seq.length() == 15) {
                System.out.println("      ⚠️ 16-digit sequence found (VID)");
                return false;
            }
        }
        
        // Check if our Aadhaar pattern exists
        String aadhaarWithSpaces = aadhaarNumber.substring(0, 4) + " " + 
                                   aadhaarNumber.substring(4, 8) + " " + 
                                   aadhaarNumber.substring(8, 12);
        
        if (!line.contains(aadhaarWithSpaces)) {
            System.out.println("      ⚠️ Aadhaar pattern not found in line text");
            return false;
        }
        
        // Look for VID keyword
        if (line.toUpperCase().contains("VID")) {
            System.out.println("      ⚠️ VID keyword found on same line");
            return false;
        }
        
        // Validate no other 12-digit sequences
        for (String seq : digitSequences) {
            if (seq.length() == 12 && !seq.equals(aadhaarNumber)) {
                System.out.println("      ⚠️ Different 12-digit sequence found");
                return false;
            }
        }
        
        System.out.println("      ✅ Valid Aadhaar in clean text context");
        return true;
    }

    /**
     * Helper: Format digit string with spaces every N digits
     */
    private String formatDigits(String digits, int groupSize) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < digits.length(); i += groupSize) {
            if (i > 0) formatted.append(" ");
            formatted.append(digits.substring(i, Math.min(i + groupSize, digits.length())));
        }
        return formatted.toString();
    }

    /**
     * Helper: Extract digit sequences
     */
    private List<String> extractDigitSequences(String text) {
        List<String> sequences = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(text.replaceAll("\\s+", ""));
        
        while (matcher.find()) {
            String seq = matcher.group();
            if (seq.length() >= 4) {
                sequences.add(seq);
            }
        }
        
        return sequences;
    }

    /**
     * Check for pure digits
     */
    private boolean isPureDigits(String text, int expectedLength) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String cleaned = text.replaceAll("\\s+", "");
        return cleaned.matches("\\d{" + expectedLength + "}");
    }



//    /**
//     * SIMPLIFIED: hasFourthGroupInSequence - keep strict vertical check only
//     * REPLACE your existing hasFourthGroupInSequence() method with this
//     */
//    private boolean hasFourthGroupInSequence(List<NumWordDto> fourDigitGroups, 
//                                             NumWordDto g1, NumWordDto g2, NumWordDto g3,
//                                             NumWordDto left, NumWordDto right, int yTolerance) {
//        
//        int centerY = (g1.rect.y + g2.rect.y + g3.rect.y) / 3;
//        
//        // Calculate gaps between our 3 groups
//        List<NumWordDto> sorted = Arrays.asList(g1, g2, g3);
//        sorted.sort(Comparator.comparingInt(nw -> nw.rect.x));
//        
//        int gap1 = sorted.get(1).rect.x - (sorted.get(0).rect.x + sorted.get(0).rect.width);
//        int gap2 = sorted.get(2).rect.x - (sorted.get(1).rect.x + sorted.get(1).rect.width);
//        int avgGap = (gap1 + gap2) / 2;
//        
//        System.out.println("      Checking for 4th group: Our 3 groups Y range = " + 
//                           g1.rect.y + "-" + g3.rect.y + " (center=" + centerY + ")");
//        
//        for (NumWordDto fourth : fourDigitGroups) {
//            if (fourth == g1 || fourth == g2 || fourth == g3) continue;
//            
//            // STRICT: Must be on exact same line (within 10px vertically)
//            int fourthCenterY = fourth.rect.y + fourth.rect.height / 2;
//            int verticalDiff = Math.abs(fourthCenterY - centerY);
//            
//            if (verticalDiff > 10) {
//                continue; // Different line - skip silently
//            }
//            
//            // Check if it's adjacent with consistent spacing
//            int leftMostX = left.rect.x;
//            int rightMostX = right.rect.x + right.rect.width;
//            
//            if (fourth.rect.x > rightMostX) {
//                int gap = fourth.rect.x - rightMostX;
//                if (gap > 5 && gap < 80 && Math.abs(gap - avgGap) < 35) {
//                    System.out.println("      ⚠️ 4th group detected to right (VID pattern)");
//                    return true;
//                }
//            }
//            
//            if (fourth.rect.x + fourth.rect.width < leftMostX) {
//                int gap = leftMostX - (fourth.rect.x + fourth.rect.width);
//                if (gap > 5 && gap < 80 && Math.abs(gap - avgGap) < 35) {
//                    System.out.println("      ⚠️ 4th group detected to left (VID pattern)");
//                    return true;
//                }
//            }
//        }
//        
//        System.out.println("      ✅ No 4th group found on same line - this is valid Aadhaar");
//        return false;
//    }

    /**
     * NOTE: REMOVE or COMMENT OUT the hasAdjacentNumericText() method call
     * It's causing false positives and the new text-based validation replaces it
     */
    
    /**
     * NEW METHOD: Find all instances of a specific Aadhaar number in XXXX XXXX XXXX format
     * with strict format validation (no surrounding text/numbers)
     */
    private List<AadhaarLocationDto> findAllInstancesOfAadhaar(
            String targetAadhaar, 
            List<NumWordDto> fourDigitGroups,
            List<Word> allWords,
            int yTolerance,
            int xGapMax) {
        
        List<AadhaarLocationDto> instances = new ArrayList<>();
        Set<String> processedPositions = new HashSet<>(); // Track to avoid exact duplicates
        
        String part1 = targetAadhaar.substring(0, 4);
        String part2 = targetAadhaar.substring(4, 8);
        String part3 = targetAadhaar.substring(8, 12);
        
        System.out.println("   Looking for pattern: " + part1 + " + " + part2 + " + " + part3);

        // Try all combinations of 3 four-digit groups
        for (int i = 0; i < fourDigitGroups.size() - 2; i++) {
            for (int j = i + 1; j < fourDigitGroups.size() - 1; j++) {
                for (int k = j + 1; k < fourDigitGroups.size(); k++) {
                    NumWordDto g1 = fourDigitGroups.get(i);
                    NumWordDto g2 = fourDigitGroups.get(j);
                    NumWordDto g3 = fourDigitGroups.get(k);

                    // Must be on same horizontal line
                    int minY = Math.min(Math.min(g1.rect.y, g2.rect.y), g3.rect.y);
                    int maxY = Math.max(Math.max(g1.rect.y, g2.rect.y), g3.rect.y);
//                    if (maxY - minY > yTolerance) {
//                        continue;
//                    }

                    // Sort by X position to determine reading order
                    List<NumWordDto> sortedByX = new ArrayList<>(Arrays.asList(g1, g2, g3));
                    sortedByX.sort(Comparator.comparingInt(nw -> nw.rect.x));
                    
                    NumWordDto left = sortedByX.get(0);
                    NumWordDto middle = sortedByX.get(1);
                    NumWordDto right = sortedByX.get(2);

                    // ============================================================
                    // STRICT FORMAT VALIDATION: Check for pure XXXX XXXX XXXX
                    // ============================================================
                    
                    // 1. Each group must be EXACTLY 4 digits (no extra text)
                    if (!isPureDigits(left.raw, 4) || 
                        !isPureDigits(middle.raw, 4) || 
                        !isPureDigits(right.raw, 4)) {
                        System.out.println("   ⚠️ Rejecting: Contains non-digit characters [" + 
                            left.raw + ", " + middle.raw + ", " + right.raw + "]");
                        continue;
                    }

//                    // 2. Check no 4th group exists (would make it VID)
//                    if (hasFourthGroupInSequence(fourDigitGroups, g1, g2, g3, left, right, yTolerance)) {
//                        System.out.println("   ⚠️ Rejecting: 4th group detected (likely VID)");
//                        continue;
//                    }

                    // 3. Check gaps between groups (must be consistent spacing)
                    int gap1 = middle.rect.x - (left.rect.x + left.rect.width);
                    int gap2 = right.rect.x - (middle.rect.x + middle.rect.width);
                    
                    if (gap1 < -10 || gap2 < -10 || gap1 > xGapMax || gap2 > xGapMax) {
                        continue;
                    }

                    // 5. Check if this matches our target Aadhaar (left-to-right)
                    boolean matchesLTR = left.digits.equals(part1) && 
                                         middle.digits.equals(part2) && 
                                         right.digits.equals(part3);
                    
                    // 6. Check if this matches our target Aadhaar (right-to-left)
                    boolean matchesRTL = right.digits.equals(part1) && 
                                         middle.digits.equals(part2) && 
                                         left.digits.equals(part3);
                    
                    if (!matchesLTR && !matchesRTL) {
                        continue; // Not our target number
                    }

                 // Calculate bounding box with padding
                    int PADDING = 10; // adjust 2-5 as needed

                    int paddedMinY = minY - PADDING;
                    int paddedMaxY = maxY + PADDING;
                    int paddedMinX = left.rect.x - PADDING;
                    int paddedMaxX = right.rect.x + right.rect.width + PADDING;

                    // Create unique position key to avoid exact duplicates
                    String posKey = paddedMinX + "," + paddedMinY + "," + (paddedMaxX - paddedMinX) + "," + (paddedMaxY - paddedMinY);

                    if (processedPositions.contains(posKey)) {
                        System.out.println("   ⚠️ Skipping duplicate detection at same position");
                        continue; // Already processed this exact location
                    }

                    // Create digit boxes in correct order
                    List<Rectangle> digitBoxes = matchesLTR
                            ? Arrays.asList(left.rect, middle.rect, right.rect)
                            : Arrays.asList(right.rect, middle.rect, left.rect);

                    AadhaarLocationDto location = new AadhaarLocationDto(
                            targetAadhaar,
                            paddedMinX,
                            paddedMinY,
                            paddedMaxX - paddedMinX,
                            paddedMaxY - paddedMinY,
                            digitBoxes
                    );

                    instances.add(location);
                    processedPositions.add(posKey);

                    System.out.println("   ✅ Instance found at (" + paddedMinX + "," + paddedMinY + ")");
                }
            }
        }

        return instances;
    }
    
//    /**
//     * Check for any other numeric text adjacent to our 3-group pattern
//     * This helps filter out patterns like: "ABC 1234 5678 9012" or "1234 5678 9012 XYZ"
//     */
//    private boolean hasAdjacentNumericText(List<NumWordDto> fourDigitGroups,
//                                          NumWordDto g1, NumWordDto g2, NumWordDto g3,
//                                          NumWordDto left, NumWordDto right, int yTolerance) {
//        int leftBoundary = left.rect.x;
//        int rightBoundary = right.rect.x + right.rect.width;
//        int centerY = left.rect.y + left.rect.height / 2;
//        
//        final int PROXIMITY_THRESHOLD = 80; // pixels
//        
//        for (NumWordDto candidate : fourDigitGroups) {
//            // Skip our current 3 groups
//            if (candidate == g1 || candidate == g2 || candidate == g3) {
//                continue;
//            }
//            
//            // Check if on same line (strict)
//            if (Math.abs(candidate.rect.y - left.rect.y) > 20) {
//                continue;
//            }
//            
//            // Check if immediately adjacent (left or right)
//            int candidateRightEdge = candidate.rect.x + candidate.rect.width;
//            
//            // Too close to left boundary
//            if (candidateRightEdge < leftBoundary && 
//                (leftBoundary - candidateRightEdge) < PROXIMITY_THRESHOLD) {
//                System.out.println("      Adjacent numeric to left: " + candidate.digits);
//                return true;
//            }
//            
//            // Too close to right boundary
//            if (candidate.rect.x > rightBoundary && 
//                (candidate.rect.x - rightBoundary) < PROXIMITY_THRESHOLD) {
//                System.out.println("      Adjacent numeric to right: " + candidate.digits);
//                return true;
//            }
//        }
//        
//        return false;
//    }

    private boolean isNearVIDKeyword(List<Word> words, int startIndex, int anchorY, int yTolerance) {
        int searchStart = Math.max(0, startIndex - 5);
        int searchEnd = Math.min(words.size(), startIndex + 8);

        for (int i = searchStart; i < searchEnd; i++) {
            Word word = words.get(i);
            String text = word.getText().toUpperCase().trim();
            if ((text.contains("VID") || text.equals("VID:") || text.equals("VID")) &&
                Math.abs(word.getBoundingBox().y - anchorY) <= (yTolerance * 3)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearSuspiciousContext(List<Word> allWords, Rectangle candidateRect, 
                                           int yTolerance, String aadhaarDigits) {
        final int searchRadius = 200;
        final int verticalTolerance = yTolerance * 2;
        
        int suspiciousCount = 0;

        for (Word word : allWords) {
            Rectangle wordRect = word.getBoundingBox();
            String text = word.getText().toUpperCase().trim();

            if (Math.abs(wordRect.y - candidateRect.y) > verticalTolerance) {
                continue;
            }

            int horizontalDist = Math.max(0, 
                Math.max(
                    candidateRect.x - (wordRect.x + wordRect.width),
                    wordRect.x - (candidateRect.x + candidateRect.width)
                )
            );

            if (horizontalDist > searchRadius) {
                continue;
            }

            if (text.equals("MOBILE") || text.equals("PHONE") || text.contains("MOBILE:") ||
                text.equals("ACCOUNT") || text.equals("A/C") || text.contains("ACCOUNT")) {
                return true;
            }

            String digits = text.replaceAll("\\D+", "");
            if (digits.length() >= 6 && text.contains("/") && 
                (text.contains("DOB") || text.contains("DATE"))) {
                suspiciousCount++;
            }

            if (digits.length() == 10 && !text.contains("/") && digits.matches("[6-9]\\d{9}")) {
                suspiciousCount++;
            }
        }

        if (suspiciousCount >= 3) {
            return true;
        }

        return false;
    }

    private boolean isValidAadhaarFormat(String aadhaar) {
        if (aadhaar == null || !aadhaar.matches("\\d{12}")) {
            return false;
        }
        
        char firstDigit = aadhaar.charAt(0);
        if (firstDigit == '0' || firstDigit == '1') {
            return false;
        }

        return isValidAadhaar(aadhaar);
    }

    public static boolean isValidAadhaar(String aadhaar) {
        if (aadhaar == null || !aadhaar.matches("\\d{12}")) {
            return false;
        }
        
        int[][] d = {
            {0,1,2,3,4,5,6,7,8,9},
            {1,2,3,4,0,6,7,8,9,5},
            {2,3,4,0,1,7,8,9,5,6},
            {3,4,0,1,2,8,9,5,6,7},
            {4,0,1,2,3,9,5,6,7,8},
            {5,9,8,7,6,0,4,3,2,1},
            {6,5,9,8,7,1,0,4,3,2},
            {7,6,5,9,8,2,1,0,4,3},
            {8,7,6,5,9,3,2,1,0,4},
            {9,8,7,6,5,4,3,2,1,0}
        };

        int[][] p = {
            {0,1,2,3,4,5,6,7,8,9},
            {1,5,7,6,2,8,3,0,9,4},
            {5,8,0,3,7,9,6,1,4,2},
            {8,9,1,6,0,4,3,5,2,7},
            {9,4,5,3,1,2,6,8,7,0},
            {4,2,8,6,5,7,3,9,0,1},
            {2,7,9,3,8,0,6,4,1,5},
            {7,0,4,6,9,1,3,2,5,8}
        };

        int c = 0;
        int[] myArray = new int[aadhaar.length()];
        for (int i = 0; i < aadhaar.length(); i++) {
            myArray[i] = Integer.parseInt(aadhaar.substring(aadhaar.length() - i - 1, aadhaar.length() - i));
        }
        for (int i = 0; i < myArray.length; i++) {
            c = d[c][p[i % 8][myArray[i]]];
        }
        return c == 0;
    }
}