package nio;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;

public class CopyFileUsingNIO {

	private static final String files = System.getProperty("user.dir")
			+ "/files/";

	public static void main(String[] args) throws IOException {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Input file to copy (from files dir)");
		String fileToCopy = scanner.nextLine();
		String fileToCopyFullPath = files + fileToCopy;
		System.out.println("Input destination ");
		String destination = scanner.nextLine() + "/" + fileToCopy;
		
		FileInputStream fIn;
		FileOutputStream fOut;
		FileChannel fIChan, fOChan;
		long fSize;
		MappedByteBuffer mBuf;

		fIn = new FileInputStream(fileToCopyFullPath);
		fOut = new FileOutputStream(destination);

		fIChan = fIn.getChannel();
		fOChan = fOut.getChannel();

		fSize = fIChan.size(); 
		mBuf = fIChan.map(FileChannel.MapMode.READ_ONLY, 0, fSize);   	// Channel -> Buffer
		fOChan.write(mBuf); // this copies the file						// Buffer -> Channel

		fIChan.close();
		fIn.close();

		fOChan.close();
		fOut.close();
	}

}
