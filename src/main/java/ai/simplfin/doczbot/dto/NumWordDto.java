package ai.simplfin.doczbot.dto;

import java.awt.Rectangle;

import net.sourceforge.tess4j.Word;

// Helper class for numeric words
public class NumWordDto {
	public String raw;
    public String digits;
    public Rectangle rect;
    public int index;

    public NumWordDto(Word w, int idx) {
        this.raw = w.getText();
        this.digits = raw.replaceAll("\\D+", "");
        this.rect = w.getBoundingBox();
        this.index = idx;
    }

    public boolean isFourDigits() {
        return digits.length() == 4;
    }

    @Override
    public String toString() {
        return digits + "@(" + rect.x + "," + rect.y + ")";
    }
}