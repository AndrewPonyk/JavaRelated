package com.ap;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hello world!
 *
 */
public class ZipHelloWorld
{
    public static void main( String[] args ) throws IOException {
        byte[] buffer = new byte[1024];

        //FileOutputStream fos = new FileOutputStream("C:\\temp\\MyFile.zip"); // ZipOutputStream relies on some output stream
        ByteOutputStream fos = new ByteOutputStream();
        ZipOutputStream zos = new ZipOutputStream(fos);

        // create fist entry from disk
        ZipEntry ze= new ZipEntry("spy.log");
        zos.putNextEntry(ze);
        FileInputStream in = new FileInputStream("C:\\temp\\spy.log");

        int len;
        while ((len = in.read(buffer)) > 0) {
            zos.write(buffer, 0, len);
            System.out.println(Arrays.toString(buffer));
            System.out.println(len);
        }
        in.close();
        zos.closeEntry();

        // create second entry in archive from hardcoded bytes array
        ze= new ZipEntry("spy2.log");
        zos.putNextEntry(ze);
        zos.write(new byte[]{48, 49, 50, 51, 52, 53}, 0, 6); // this is '012345' coded like bytes
        zos.closeEntry();

        // close archive stream
        zos.close();
        System.out.println(fos.getCount());
    }
}
