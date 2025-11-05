package ai.simplfin.doczbot.dto;

import java.awt.Rectangle;
import java.util.List;

public  class AadhaarLocationDto {
    private String uid;
    private int x, y, width, height;
    private List<Rectangle> digitBoxes;

    public AadhaarLocationDto(String uid, int x, int y, int width, int height, List<Rectangle> digitBoxes) {
        this.uid = uid;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.digitBoxes = digitBoxes;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<Rectangle> getDigitBoxes() {
        return digitBoxes;
    }

    public void setDigitBoxes(List<Rectangle> digitBoxes) {
        this.digitBoxes = digitBoxes;
    }
}