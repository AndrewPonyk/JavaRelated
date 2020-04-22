package com.apress.spring;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GetScreen {
    public static void main(String[] args) throws AWTException, IOException {
        System.out.println("test");
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage capture = new Robot().createScreenCapture(screenRect);
        ImageIO.write(capture, "png", new File("/home/andrii/scree.png"));
    }
}
