package ai.simplfin.doczbot.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Main response DTO for document text extraction
 * Based on your existing IdDocumentExtractionResult structure
 */
public class IdDocumentExtractionResult {
    
    private String id;
    private String fileName;
    private String fileType;
    private String idType;
    private IdDocumentContent content;
    private LocalDateTime extractionTime;
    private String extractionMethod;
    private Long processingTimeMs;
    private Boolean success;
    private String errorCode; // ✅ NEW FIELD
    private String errorMessage;
    
    
    // Private constructor for builder
    private IdDocumentExtractionResult(Builder builder) {
        this.id = builder.id;
        this.fileName = builder.fileName;
        this.fileType = builder.fileType;
        this.idType = builder.idType;
        this.content = builder.content;
        this.extractionTime = builder.extractionTime;
        this.extractionMethod = builder.extractionMethod;
        this.processingTimeMs = builder.processingTimeMs;
        this.success = builder.success;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
    }

    public IdDocumentExtractionResult() {
    }

    // Manual builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String fileName;
        private String fileType;
        private String idType;
        private IdDocumentContent content;
        private LocalDateTime extractionTime;
        private String extractionMethod;
        private Long processingTimeMs;
        private Boolean success;
        private String errorCode;
        private String errorMessage;

        public Builder id(String id) { this.id = id; return this; }
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder fileType(String fileType) { this.fileType = fileType; return this; }
        public Builder idType(String idType) { this.idType = idType; return this; }
        public Builder content(IdDocumentContent content) { this.content = content; return this; }
        public Builder extractionTime(LocalDateTime extractionTime) { this.extractionTime = extractionTime; return this; }
        public Builder extractionMethod(String extractionMethod) { this.extractionMethod = extractionMethod; return this; }
        public Builder processingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; return this; }
        public Builder success(Boolean success) { this.success = success; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public IdDocumentExtractionResult build() {
            return new IdDocumentExtractionResult(this);
        }
    }
    
    /**
     * Nested class for document content details
     */
    public static class IdDocumentContent {
        private String referenceId;
        private String firstName;
        private String lastName;
        private String middleName;
        private String gender;
        private String dateOfBirth;
        private String mobileNo;
        private String expiryDate;
        private String issuedDate;
        private String issuedBy;
        private AddressDetails addressDetails;
        private String maskedFilePath;
        private int aadhaarCount;
        private String maskedAadhaarNumbers;
        private String maskedFileUrl;
        private String originalFileName;
        private String maskingType;
        private String maskedFileBase64; // ✅ CHANGED: Base64 string instead of byte[]
        private String maskedFileContentType; // ✅ Content type
        private List<AdditionalDetail> additionalDetails;
        private String rawExtractedText;
        
        // ===== Full constructor =====
        public IdDocumentContent(String referenceId, String firstName, String lastName, String middleName,
                                 String gender, String dateOfBirth, String mobileNo, String expiryDate,
                                 String issuedDate, String issuedBy, AddressDetails addressDetails,
                                 List<AdditionalDetail> additionalDetails, String rawExtractedText) {
            this.referenceId = referenceId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.middleName = middleName;
            this.gender = gender;
            this.dateOfBirth = dateOfBirth;
            this.mobileNo = mobileNo;
            this.expiryDate = expiryDate;
            this.issuedDate = issuedDate;
            this.issuedBy = issuedBy;
            this.addressDetails = addressDetails;
            this.additionalDetails = additionalDetails != null ? additionalDetails : new ArrayList<>();
            this.rawExtractedText = rawExtractedText;
        }

        // Optional: default constructor
        public IdDocumentContent() {
            this.additionalDetails = new ArrayList<>();
        }
        
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String referenceId;
            private String firstName;
            private String lastName;
            private String middleName;
            private String gender;
            private String dateOfBirth;
            private String mobileNo;
            private String expiryDate;
            private String issuedDate;
            private String issuedBy;
            private AddressDetails addressDetails;
            private List<AdditionalDetail> additionalDetails = new ArrayList<>();
            private String rawExtractedText;

            public Builder referenceId(String referenceId) { this.referenceId = referenceId; return this; }
            public Builder firstName(String firstName) { this.firstName = firstName; return this; }
            public Builder lastName(String lastName) { this.lastName = lastName; return this; }
            public Builder middleName(String middleName) { this.middleName = middleName; return this; }
            public Builder gender(String gender) { this.gender = gender; return this; }
            public Builder dateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; return this; }
            public Builder mobileNo(String mobileNo) { this.mobileNo = mobileNo; return this; }
            public Builder expiryDate(String expiryDate) { this.expiryDate = expiryDate; return this; }
            public Builder issuedDate(String issuedDate) { this.issuedDate = issuedDate; return this; }
            public Builder issuedBy(String issuedBy) { this.issuedBy = issuedBy; return this; }
            public Builder addressDetails(AddressDetails addressDetails) { this.addressDetails = addressDetails; return this; }
            public Builder additionalDetails(List<AdditionalDetail> additionalDetails) { this.additionalDetails = additionalDetails; return this; }
            public Builder rawExtractedText(String rawExtractedText) { this.rawExtractedText = rawExtractedText; return this; }

            public IdDocumentContent build() {
                return new IdDocumentContent(referenceId, firstName, lastName, middleName, gender,
                                             dateOfBirth, mobileNo, expiryDate, issuedDate, issuedBy,
                                             addressDetails, additionalDetails, rawExtractedText);
            }
        }

        // ✅ NEW GETTER/SETTER for maskedFileContentType
        public String getMaskedFileContentType() {
            return maskedFileContentType;
        }

        public void setMaskedFileContentType(String maskedFileContentType) {
            this.maskedFileContentType = maskedFileContentType;
        }

        public String getMaskedFilePath() {
            return maskedFilePath;
        }

        public void setMaskedFilePath(String maskedFilePath) {
            this.maskedFilePath = maskedFilePath;
        }

        public int getAadhaarCount() {
            return aadhaarCount;
        }

        public String getMaskedFileUrl() {
            return maskedFileUrl;
        }

        public void setMaskedFileUrl(String maskedFileUrl) {
            this.maskedFileUrl = maskedFileUrl;
        }

        public void setAadhaarCount(int aadhaarCount) {
            this.aadhaarCount = aadhaarCount;
        }

        public String getMaskedAadhaarNumbers() {
            return maskedAadhaarNumbers;
        }

        public void setMaskedAadhaarNumbers(String maskedAadhaarNumbers) {
            this.maskedAadhaarNumbers = maskedAadhaarNumbers;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        public String getReferenceId() {
            return referenceId;
        }
        
        public void setReferenceId(String referenceId) {
            this.referenceId = referenceId;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
        
        public String getMiddleName() {
            return middleName;
        }
        
        public void setMiddleName(String middleName) {
            this.middleName = middleName;
        }
        
        public String getGender() {
            return gender;
        }
        
        public void setGender(String gender) {
            this.gender = gender;
        }
        
        public String getDateOfBirth() {
            return dateOfBirth;
        }
        
        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }
        
        public String getMobileNo() {
            return mobileNo;
        }
        
        public void setMobileNo(String mobileNo) {
            this.mobileNo = mobileNo;
        }
        
        public String getExpiryDate() {
            return expiryDate;
        }
        
        public void setExpiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
        }
        
        public String getIssuedDate() {
            return issuedDate;
        }
        
        public void setIssuedDate(String issuedDate) {
            this.issuedDate = issuedDate;
        }
        
        public String getIssuedBy() {
            return issuedBy;
        }
        
        public void setIssuedBy(String issuedBy) {
            this.issuedBy = issuedBy;
        }
        
        public AddressDetails getAddressDetails() {
            return addressDetails;
        }
        
        public void setAddressDetails(AddressDetails addressDetails) {
            this.addressDetails = addressDetails;
        }
        
        public List<AdditionalDetail> getAdditionalDetails() {
            return additionalDetails;
        }
        
        public void setAdditionalDetails(List<AdditionalDetail> additionalDetails) {
            this.additionalDetails = additionalDetails;
        }
        
        public String getRawExtractedText() {
            return rawExtractedText;
        }
        
        public void setRawExtractedText(String rawExtractedText) {
            this.rawExtractedText = rawExtractedText;
        }

        public String getMaskingType() {
            return maskingType;
        }

        public void setMaskingType(String maskingType) {
            this.maskingType = maskingType;
        }

		public String getMaskedFileBase64() {
			return maskedFileBase64;
		}

		public void setMaskedFileBase64(String maskedFileBase64) {
			this.maskedFileBase64 = maskedFileBase64;
		}
    }
    
    /**
     * Address details nested class
     */
    public static class AddressDetails {
        private String address1;
        private String address2;
        private String address3;
        private String address4;
        private String city;
        private String state;
        private String zipCode;
        private String country;
        
        // ===== Full constructor =====
        public AddressDetails(String address1, String address2, String address3, String address4,
                              String city, String state, String zipCode, String country) {
            this.address1 = address1;
            this.address2 = address2;
            this.address3 = address3;
            this.address4 = address4;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.country = country;
        }

        // Optional: default constructor
        public AddressDetails() { }

        // ===== Builder =====
        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String address1;
            private String address2;
            private String address3;
            private String address4;
            private String city;
            private String state;
            private String zipCode;
            private String country;

            public Builder address1(String address1) { this.address1 = address1; return this; }
            public Builder address2(String address2) { this.address2 = address2; return this; }
            public Builder address3(String address3) { this.address3 = address3; return this; }
            public Builder address4(String address4) { this.address4 = address4; return this; }
            public Builder city(String city) { this.city = city; return this; }
            public Builder state(String state) { this.state = state; return this; }
            public Builder zipCode(String zipCode) { this.zipCode = zipCode; return this; }
            public Builder country(String country) { this.country = country; return this; }

            public AddressDetails build() {
                return new AddressDetails(address1, address2, address3, address4, city, state, zipCode, country);
            }
        }
        
        public String getAddress1() {
            return address1;
        }
        
        public void setAddress1(String address1) {
            this.address1 = address1;
        }
        
        public String getAddress2() {
            return address2;
        }
        
        public void setAddress2(String address2) {
            this.address2 = address2;
        }
        
        public String getAddress3() {
            return address3;
        }
        
        public void setAddress3(String address3) {
            this.address3 = address3;
        }
        
        public String getAddress4() {
            return address4;
        }
        
        public void setAddress4(String address4) {
            this.address4 = address4;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public String getState() {
            return state;
        }
        
        public void setState(String state) {
            this.state = state;
        }
        
        public String getZipCode() {
            return zipCode;
        }
        
        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }
        
        public String getCountry() {
            return country;
        }
        
        public void setCountry(String country) {
            this.country = country;
        }
    }
    
    /**
     * Additional details for extra information
     */
    public static class AdditionalDetail {
        private String info;
        private String value;
        private Double confidence;
        
        public AdditionalDetail(String info, String value, Double confidence) {
            this.info = info;
            this.value = value;
            this.confidence = confidence;
        }
        
        public String getInfo() {
            return info;
        }
        
        public void setInfo(String info) {
            this.info = info;
        }
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public Double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
        
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String info;
            private String value;
            private Double confidence;

            public Builder info(String info) { this.info = info; return this; }
            public Builder value(String value) { this.value = value; return this; }
            public Builder confidence(Double confidence) { this.confidence = confidence; return this; }

            public AdditionalDetail build() {
                return new AdditionalDetail(info, value, confidence);
            }
        }
    }
    
    /**
     * Enum for ID types
     */
    public enum IdType {
        AADHAR("AADHAR"),
        PAN("PAN"),
        DRIVING_LICENSE("DRIVING_LICENSE"),
        PASSPORT("PASSPORT"),
        OTHER("OTHER");
        
        private final String value;
        
        IdType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }

    // ===== GETTERS AND SETTERS =====
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public IdDocumentContent getContent() {
        return content;
    }

    public void setContent(IdDocumentContent content) {
        this.content = content;
    }

    public LocalDateTime getExtractionTime() {
        return extractionTime;
    }

    public void setExtractionTime(LocalDateTime extractionTime) {
        this.extractionTime = extractionTime;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Boolean getSuccess() {
        return success;
    }
    
    public boolean isSuccess() {
        return success != null && success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    // ✅ NEW GETTER/SETTER for errorCode
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