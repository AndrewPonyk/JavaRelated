import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Task1App {
    public static void main(String[] args) {
        removeSubfolders("D:\\Temp\\testD", "D:\\Temp\\testR");
    }

    public static void removeSubfolders(String folderDPath, String folderRPath){
        File folderD = new File(folderDPath);
        File folderR = new File(folderRPath);

        if(!folderD.isDirectory()){
            throw new IllegalArgumentException(folderDPath + " is not a directory");
        }

        List<String> folderRSubfolders = Arrays.stream(folderR.listFiles())
                .filter(File::isDirectory)
                .map(File::getName).collect(Collectors.toList());

        System.out.println(folderRSubfolders);
        Arrays.stream(folderD.listFiles()).forEach(e->{
            if(folderRSubfolders.contains(e.getName())){
                System.out.println("Deleting folder" + e.getAbsolutePath());
                e.delete();
                System.out.println("Deleted folder" + e.getAbsolutePath());
            }
        });
    }
}
