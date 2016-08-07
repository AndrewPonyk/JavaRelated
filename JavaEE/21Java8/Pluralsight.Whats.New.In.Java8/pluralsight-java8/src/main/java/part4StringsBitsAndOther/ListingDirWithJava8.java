package part4StringsBitsAndOther;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ListingDirWithJava8 {
	public static void main(String[] args) {
		System.out.println("Listing directories with java 8");

		// to visit whole subtree use Files.walk
		// to list only one level in directory use Files.list
		try (Stream<Path> dirList = Files.list(Paths
				.get("/home/andrew/git/minerva"))) {

			dirList.filter(path -> path.toFile().isDirectory()).forEach(
					System.out::println);

			printDirStructureWithFormat("/media/andrew/Transcend/Vidokursu_and_Books_From_118a/3.1JAVASCRIPT");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void printDirStructureWithFormat(String root)
			throws IOException {
		final int countSeparators = root.length()
				- root.replace(File.separator, "").length();
		System.out.println("\nprintDirStructureWithFormat\n");
		Stream<Path> dirList = Files.walk(Paths.get(root));

		dirList.filter(path -> path.toFile().isDirectory() && !path.toFile().toString().contains("JVM")).forEach(
				(e) -> {
					String filePath = e.toString();
					Integer count = filePath.length()
							- filePath.replace(File.separator, "").length();
					if(count - countSeparators <=2){
						IntStream.range(0, count - countSeparators).forEach(
								(i) -> {
									System.out.print("\t");
								});
						System.out.print("| " + filePath + " \n");
					}
				});
		dirList.close();
	}
}
