package clipboard;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;

/**
 * Created by aponyk on 10.10.2016.
 */
public class ClipboardHello implements ClipboardOwner {
    public static void main(String[] args) {
        ClipboardHello hello = new ClipboardHello();

        System.out.println("Now in clipboard:"  + hello.getClipboardContents());
        System.out.println("Setup new Text into CLIPBOARD: " + "12345");
        hello.setClipboardContents("12345");
    }

    public String getClipboardContents(){
        String result = "";

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);

        boolean hasTransferableText = (contents != null) && contents.isDataFlavorSupported(DataFlavor.stringFlavor);
        if(hasTransferableText){
            try {
                result = (String) contents.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public void setClipboardContents(String aString){
        StringSelection stringSelection = new StringSelection(aString);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, this);
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {

    }
}
