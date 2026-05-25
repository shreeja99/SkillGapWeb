package com.skillgap.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * SERVICE: ResumeParserService
 * OOP: Abstraction — hides all PDF reading and skill extraction logic.
 *
 * Flow:
 *   1. Receive uploaded PDF (MultipartFile)
 *   2. Use Apache PDFBox to extract raw text
 *   3. If text is empty (image-based PDF), fall back to Tesseract OCR
 *   4. Send text to Groq API with an extraction prompt
 *   5. Return comma-separated skill string back to controller
 */
@Service
public class ResumeParserService {

    @Autowired
    private ApiService apiService;

    // Path to Tesseract installation on Windows
    // Change this if you installed Tesseract in a different location
    private static final String TESSERACT_PATH = "C:/Program Files/Tesseract-OCR";

    /**
     * Main public method.
     * Accepts a PDF file, extracts text, asks Groq to identify skills.
     *
     * @param file  Uploaded PDF resume
     * @return      Comma-separated skill string  e.g. "java, sql, git, spring boot"
     */
    public String extractSkillsFromPdf(MultipartFile file) throws IOException {

        // ── Step 1: Validate file ──────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }

        // ── Step 2: Extract raw text from PDF using PDFBox ─────────────────
        String resumeText = extractTextFromPdf(file);

        // ── Step 3: If PDFBox got nothing, try OCR ─────────────────────────
        if (resumeText == null || resumeText.trim().isEmpty()) {
            System.out.println("[ResumeParser] PDFBox found no text — trying OCR...");
            resumeText = extractTextWithOcr(file);
        }

        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new IOException("Could not extract any text from the PDF. " +
                    "Make sure Tesseract is installed at: " + TESSERACT_PATH);
        }

        System.out.println("[DEBUG] Extracted text sample: " +
                resumeText.substring(0, Math.min(300, resumeText.length())));
        System.out.println("[ResumeParser] Extracted " + resumeText.length() + " characters from PDF.");

        // ── Step 4: Trim text to avoid exceeding API token limits ──────────
        // Keep first 3000 characters — enough to cover skills sections
        String trimmedText = resumeText.length() > 3000
                ? resumeText.substring(0, 3000)
                : resumeText;

        // ── Step 5: Ask Groq API to extract skills ─────────────────────────
        List<String> skills = apiService.extractSkillsFromResumeText(trimmedText);

        if (skills.isEmpty()) {
            throw new IOException("No skills could be identified from the resume.");
        }

        // ── Step 6: Join into comma-separated string for the frontend ──────
        return String.join(", ", skills);
    }

    /**
     * Uses Apache PDFBox 3.x to read all text from a PDF.
     * Works only for text-based PDFs.
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * Fallback: renders each PDF page as an image, then runs Tesseract OCR on it.
     * Used when PDFBox returns empty text (image-based / scanned PDFs).
     */
    private String extractTextWithOcr(MultipartFile file) throws IOException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(TESSERACT_PATH + "/tessdata");
        tesseract.setLanguage("eng");

        StringBuilder ocrText = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                // Render page at 300 DPI for good OCR accuracy
                BufferedImage image = renderer.renderImageWithDPI(page, 300, ImageType.RGB);

                try {
                    String pageText = tesseract.doOCR(image);
                    ocrText.append(pageText).append("\n");
                    System.out.println("[ResumeParser] OCR completed page " + (page + 1));
                } catch (TesseractException e) {
                    System.out.println("[ResumeParser] OCR failed on page " + (page + 1) + ": " + e.getMessage());
                }
            }
        }

        return ocrText.toString().trim();
    }
}