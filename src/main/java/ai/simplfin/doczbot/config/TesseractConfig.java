package ai.simplfin.doczbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.sourceforge.tess4j.Tesseract;

@Configuration
public class TesseractConfig {
		    @Bean
		    public Tesseract tesseract() {
		        Tesseract tess = new Tesseract();

		        // Use environment variable for Docker, fallback to Windows path
		        String tessDataPath = System.getenv("TESSDATA_PREFIX");
		        if (tessDataPath != null && !tessDataPath.isEmpty()) {
		            tess.setDatapath(tessDataPath);
		        } else {
		            tess.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
		        }

		        tess.setLanguage("eng");
		        tess.setPageSegMode(11);
		        tess.setOcrEngineMode(3);

		        return tess;
		    }
		}

