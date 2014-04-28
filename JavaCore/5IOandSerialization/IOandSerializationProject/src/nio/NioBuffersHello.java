package nio;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioBuffersHello {
	private static final String files = System.getProperty("user.dir")
			+ "/files/";

	public static void main(String[] args) {
		readFromFileUsingNIO();
		
		writeToFileUsingNIOChannelsAndBuffers();
	}

	public static void writeToFileUsingNIOChannelsAndBuffers() {
		FileOutputStream fileOutputStream;
		FileChannel fileChannel;
		ByteBuffer byteBuffer;

		try {
			fileOutputStream = new FileOutputStream(files + "testNIO.txt");
			fileChannel = fileOutputStream.getChannel();
			byteBuffer = ByteBuffer.allocateDirect(256);
		
			
			/*for (int i = 0; i < 26; i++)
				byteBuffer.put((byte) ('A' + i));
			*/
		
			byteBuffer.put("Hello , world".getBytes());
			
			byteBuffer.flip(); // without flip writes null bytes
			byteBuffer.rewind();
			fileChannel.write(byteBuffer);
			fileChannel.close();
			fileOutputStream.close();
		} catch (IOException exc) {
			System.out.println(exc);
		}
	}

	public static void readFromFileUsingNIO() {
		try {
			RandomAccessFile aFile = new RandomAccessFile(files + "file2.txt",
					"rw");

			FileChannel inChannel = aFile.getChannel();

			// create buffer with capacity of 8 bytes
			ByteBuffer buf = ByteBuffer.allocate(16);

			int bytesRead = inChannel.read(buf); // read into buffer.

			while (bytesRead != -1) {
				buf.flip(); // make buffer ready for read

				while (buf.hasRemaining()) {

					System.out.print((char) buf.get()); // read 1 byte at a time
				}

				buf.clear(); // make buffer ready for writing
				bytesRead = inChannel.read(buf);
			}
			aFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}