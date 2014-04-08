package file;

import java.io.File;
import java.io.FilenameFilter;

public class ListFilesInDirectoryWithSomeExtension {
	public static void main(final String[] args) {

		if (args.length != 2) {
			System.err.println("Please set Directory name and file extension");
			return;
		}

		File file = new File(args[0]);

		FilenameFilter fnf = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(args[1]);
			}
		};

		String[] list = file.list(fnf);

		System.out.println( String.format("Files in  '%s' directory with '%s' extension", args[0], args[0]));
		for (String item : list) {
			System.out.println(item);
		}
		
	}
}