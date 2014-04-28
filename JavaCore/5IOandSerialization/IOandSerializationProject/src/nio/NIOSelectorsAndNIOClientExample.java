package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

class NIOSelectorsAndNIOClientExample {
    
    public static void main(String[] args) throws IOException,
            InterruptedException {
        int port = 4444;
        SocketChannel channel = SocketChannel.open();
 
        // we open this channel in non blocking mode
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress("localhost", port));
 
        while (!channel.finishConnect()) {
            // System.out.println("still connecting");
        }
        while (true) {
            // see if any message has been received
            ByteBuffer bufferA = ByteBuffer.allocate(20);
            int count = 0;
            String message = "";
            while ((count = channel.read(bufferA)) > 0) {
                // flip the buffer to start reading
                bufferA.flip();
                message += Charset.defaultCharset().decode(bufferA);
 
            }
 
            if (message.length() > 0) {
                System.out.println(message);
                // write some data into the channel
                CharBuffer buffer = CharBuffer.wrap("Hello Server");
                while (buffer.hasRemaining()) {
                    channel.write(Charset.defaultCharset().encode(buffer));
                }
                message = "";
            }
 
        }
    }
}

