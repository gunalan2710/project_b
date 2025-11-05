package ai.simplfin.doczbot.controllers;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ai.simplfin.doczbot.dto.IdDocumentExtractionResult;
import ai.simplfin.doczbot.services.masking.AadhaarMaskingService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/aadhaar")
@Slf4j
public class AadhaarMaskingController {

	@Autowired
	private AadhaarMaskingService maskingService;

	private static final Logger log = LoggerFactory.getLogger(AadhaarMaskingController.class);

	/**
	 * Response DTO for error cases only
	 */
	public static class AadhaarErrorResponse {
		private String imageName;
		private String errorCode;
		private String errorMessage;
		
		public AadhaarErrorResponse() {}
		
		public AadhaarErrorResponse(String imageName, String errorCode, String errorMessage) {
			this.imageName = imageName;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}
		
		public String getImageName() {
			return imageName;
		}
		
		public void setImageName(String imageName) {
			this.imageName = imageName;
		}
		
		public String getErrorCode() {
			return errorCode;
		}
		
		public void setErrorCode(String errorCode) {
			this.errorCode = errorCode;
		}
		
		public String getErrorMessage() {
			return errorMessage;
		}
		
		public void setErrorMessage(String errorMessage) {
			this.errorMessage = errorMessage;
		}
	}

	/**
	 * Endpoint for extracting and masking Aadhaar from document images
	 * Returns direct image on success, JSON error on failure
	 */
	@PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadDocumentImage(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "extractionMethod", required = false, defaultValue = "TESSERACT") String extractionMethod,
			@RequestParam(value = "masking", required = false, defaultValue = "true") String masking,
			@RequestParam(value = "idType", required = false, defaultValue = "AADHAAR") String idType,
			@RequestParam(value = "maskingType", required = false, defaultValue = "RIBBON") String maskingType) {

		try {
			log.info("Received image extraction request for file: {} with masking type: {}",
					file.getOriginalFilename(), maskingType);

			// Validate file is not empty
			if (file.isEmpty()) {
				AadhaarErrorResponse response = new AadhaarErrorResponse(null, "EMPTY_FILE", 
					"File is empty. Please upload a valid file.");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			// Validate file size (max 10MB)
			long maxSize = 10 * 1024 * 1024;
			if (file.getSize() > maxSize) {
				AadhaarErrorResponse response = new AadhaarErrorResponse(null, "FILE_TOO_LARGE",
					String.format("File size exceeds 10MB limit. Uploaded: %.2fMB", 
						file.getSize() / (1024.0 * 1024.0)));
				return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
			}

			// Validate file format early
			String contentType = file.getContentType();
			String filename = file.getOriginalFilename();

			if (!isValidFileFormat(contentType, filename)) {
				String extension = getFileExtension(filename);
				AadhaarErrorResponse response = new AadhaarErrorResponse(null, "INVALID_FILE_FORMAT",
					String.format("Invalid file format '%s'. Only PDF, JPEG, JPG, PNG formats are allowed.",
						extension.toUpperCase()));
				return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
			}

			// Validate masking type
			String normalizedMaskingType;
			try {
				normalizedMaskingType = validateAndNormalizeMaskingType(maskingType);
			} catch (IllegalArgumentException e) {
				AadhaarErrorResponse response = new AadhaarErrorResponse(null, "INVALID_MASKING_TYPE", 
					e.getMessage());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			// Process the file
			IdDocumentExtractionResult result = maskingService.maskAadhaarInDocument(file, normalizedMaskingType);

			if (result.isSuccess() && result.getContent() != null) {
				String maskedFilePath = result.getContent().getMaskedFilePath();
				
				if (maskedFilePath == null || maskedFilePath.isEmpty()) {
					log.error("Masked file path is null or empty");
					AadhaarErrorResponse response = new AadhaarErrorResponse(null, "PROCESSING_ERROR",
						"Failed to generate masked image");
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
				}

				// Read the masked image file
				File maskedFile = new File(maskedFilePath);
				if (!maskedFile.exists()) {
					log.error("Masked file not found: {}", maskedFilePath);
					AadhaarErrorResponse response = new AadhaarErrorResponse(null, "FILE_NOT_FOUND",
						"Masked image file not found");
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
				}

				// Read file as byte array
				byte[] imageBytes = Files.readAllBytes(maskedFile.toPath());
				
				// Determine content type
				String imageContentType = determineImageContentType(maskedFile.getName());
				
				// Create response headers with metadata
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.parseMediaType(imageContentType));
				headers.setContentLength(imageBytes.length);
				headers.setContentDisposition(
					ContentDisposition.builder("inline")
						.filename(maskedFile.getName())
						.build()
				);
				
				// Add custom headers for metadata
				headers.add("X-Image-Name", maskedFile.getName());
				headers.add("X-Aadhaar-Count", String.valueOf(result.getContent().getAadhaarCount()));
				headers.add("X-Masking-Type", normalizedMaskingType);
				headers.add("X-Error-Code", "null");
				headers.add("X-Error-Message", "null");

				log.info("Successfully masked file: {}. Found {} Aadhaar number(s)", 
						filename, result.getContent().getAadhaarCount());

				// Return direct image with metadata in headers
				return ResponseEntity.status(HttpStatus.OK)
						.headers(headers)
						.body(imageBytes);

			} else {
				// Masking failed or no Aadhaar found
				String errorCode = result.getErrorCode() != null ? result.getErrorCode() : "AADHAAR_NOT_FOUND";
				String errorMessage = result.getErrorMessage() != null ? 
					result.getErrorMessage() : "No Aadhaar number found in the document";
				
				log.warn("Masking failed for file: {}. Error: {}", filename, errorMessage);
				
				AadhaarErrorResponse response = new AadhaarErrorResponse(null, errorCode, errorMessage);
				HttpStatus status = getHttpStatusForErrorCode(errorCode);
				return ResponseEntity.status(status).body(response);
			}

		} catch (IllegalArgumentException e) {
			log.error("Invalid parameter for file: {}", file.getOriginalFilename(), e);
			AadhaarErrorResponse response = new AadhaarErrorResponse(null, "INVALID_PARAMETER",
				"Invalid parameter: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		} catch (Exception e) {
			log.error("Unexpected error processing file: {}", file.getOriginalFilename(), e);
			AadhaarErrorResponse response = new AadhaarErrorResponse(null, "INTERNAL_ERROR",
				"An unexpected error occurred while processing the file. Please try again.");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Determine image content type from filename
	 */
	private String determineImageContentType(String filename) {
		String extension = getFileExtension(filename).toLowerCase();
		switch (extension) {
			case "png":
				return "image/png";
			case "jpg":
			case "jpeg":
				return "image/jpeg";
			case "pdf":
				return "application/pdf";
			default:
				return "application/octet-stream";
		}
	}

	/**
	 * Validate file format (PDF, JPEG, JPG, PNG only)
	 */
	private boolean isValidFileFormat(String contentType, String filename) {
		if (contentType == null || filename == null) {
			return false;
		}

		String extension = getFileExtension(filename).toLowerCase();

		boolean validContentType = contentType.equalsIgnoreCase("application/pdf")
				|| contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/jpg")
				|| contentType.equalsIgnoreCase("image/png");

		boolean validExtension = extension.equals("pdf") || extension.equals("jpg") || extension.equals("jpeg")
				|| extension.equals("png");

		return validContentType && validExtension;
	}

	/**
	 * Get file extension from filename
	 */
	private String getFileExtension(String filename) {
		if (filename == null || filename.isEmpty()) {
			return "";
		}
		int lastDot = filename.lastIndexOf('.');
		return lastDot > 0 ? filename.substring(lastDot + 1) : "";
	}

	/**
	 * Validate and normalize masking type parameter
	 */
	private String validateAndNormalizeMaskingType(String maskingType) {
		if (maskingType == null || maskingType.trim().isEmpty()) {
			return "RIBBON";
		}

		String normalized = maskingType.trim().toUpperCase();

		if (normalized.equals("*") || normalized.equals("ASTERISK")) {
			return "ASTERISK";
		} else if (normalized.equals("X")) {
			return "X";
		} else if (normalized.equals("RIBBON") || normalized.equals("BAR") || normalized.equals("BLACK")) {
			return "RIBBON";
		} else {
			throw new IllegalArgumentException(
					"Invalid maskingType: '" + maskingType + "'. Supported values: *, X, ASTERISK, RIBBON");
		}
	}

	/**
	 * Map error codes to HTTP status codes
	 */
	private HttpStatus getHttpStatusForErrorCode(String errorCode) {
		if (errorCode == null) {
			return HttpStatus.OK;
		}

		switch (errorCode) {
		case "INVALID_FILE":
		case "INVALID_FILE_FORMAT":
		case "INVALID_MASKING_TYPE":
		case "INVALID_PARAMETER":
			return HttpStatus.BAD_REQUEST;

		case "UNSUPPORTED_FORMAT":
			return HttpStatus.UNSUPPORTED_MEDIA_TYPE;

		case "FILE_TOO_LARGE":
			return HttpStatus.PAYLOAD_TOO_LARGE;

		case "AADHAAR_NOT_FOUND":
			return HttpStatus.NOT_FOUND;

		case "FILE_READ_ERROR":
		case "INVALID_IMAGE":
		case "DATA_NOT_FOUND":
			return HttpStatus.UNPROCESSABLE_ENTITY;

		case "PROCESSING_ERROR":
		case "INTERNAL_ERROR":
		case "FILE_NOT_FOUND":
		default:
			return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}

	/**
	 * Health check endpoint
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> healthCheck() {
		Map<String, String> response = new HashMap<>();
		response.put("status", "UP");
		response.put("service", "Aadhaar Masking Service");
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("supportedMaskingTypes", "ASTERISK (*), X, RIBBON");
		return ResponseEntity.ok(response);
	}

	/**
	 * Get supported masking types
	 */
	@GetMapping("/masking-types")
	public ResponseEntity<Map<String, Object>> getSupportedMaskingTypes() {
		Map<String, Object> response = new HashMap<>();

		List<Map<String, String>> maskingTypes = new ArrayList<>();

		Map<String, String> asterisk = new HashMap<>();
		asterisk.put("type", "ASTERISK");
		asterisk.put("value", "*");
		asterisk.put("example", "**** **** 1234");
		asterisk.put("description", "Masks first 8 digits with asterisks");
		maskingTypes.add(asterisk);

		Map<String, String> x = new HashMap<>();
		x.put("type", "X");
		x.put("value", "X");
		x.put("example", "XXXX XXXX 1234");
		x.put("description", "Masks first 8 digits with X characters");
		maskingTypes.add(x);

		Map<String, String> ribbon = new HashMap<>();
		ribbon.put("type", "RIBBON");
		ribbon.put("value", "RIBBON");
		ribbon.put("example", "████ ████ 1234");
		ribbon.put("description", "Masks first 8 digits with black bar (default)");
		maskingTypes.add(ribbon);

		response.put("maskingTypes", maskingTypes);
		response.put("defaultType", "RIBBON");
		response.put("note", "VID (16-digit Virtual ID) is automatically detected and not masked");

		return ResponseEntity.ok(response);
	}
}