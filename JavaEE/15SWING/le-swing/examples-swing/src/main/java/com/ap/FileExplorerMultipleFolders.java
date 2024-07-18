package com.ap;


import ws.schild.jave.MultimediaInfo;
import ws.schild.jave.MultimediaObject;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FileExplorerMultipleFolders extends JFrame {

    public static final String SPLITTER_METADATA = "   --->>>>";

    public static HashMap<String, List<FileInfo>> folderFilesCount = new HashMap<>();

    public static Map<String, String> pconfig = new LinkedHashMap<>();

    public static Map<String, Integer> durations = new LinkedHashMap<>();

    public static Map<String, Integer> colorMapping = new HashMap<String, Integer>(){{
       put("red", 0xFF0000);
         put("green", 0x00FF00);
            put("blue", 0x0000FF);
            put("yellow", 0xFFFF00);
            put("black", 0x000000);
            put("lightpink", 0xF2B8C6);
            put("pink", 0xFC46AA);
    }};

    static {
        //read file c:\tmp\pconfig.txt
        try {
            Files.lines(Paths.get("c:\\tmp\\pconfig.txt")).forEach(e -> {
                String[] temp = e.split("=");
                pconfig.put(temp[0], temp[1]);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

//        try {
            //todo: for now DO NOT LOAD CACHE
//            FileInputStream fileIn = new FileInputStream("C:\\tmp\\folderFilesCount.ser");
//            ObjectInputStream in = new ObjectInputStream(fileIn);
//            folderFilesCount = (HashMap<String, List<FileInfo>>) in.readObject();
//            in.close();
//            fileIn.close();
//        } catch (IOException i) {
//            i.printStackTrace();
//        } catch (ClassNotFoundException c) {
//            System.out.println("Class not found");
//            c.printStackTrace();
//        }

        try {
            durations  = readMapFromFile("c:\\tmp\\durations.txt");
        } catch (Exception e) {
            System.out.printf(e.getMessage());
        }

    }

    private final JList<File> fileList;

    JPopupMenu contextMenu = new JPopupMenu();

    private final JTextField folderInputField;

    private final JCheckBox checkBoxSortBySize;

    private final JCheckBox checkBoxCheckDurations;

    private final JTextField filterInputField;
    private List<FileInfo> allFiles;

    private Integer fileCount = 0;

    public FileExplorerMultipleFolders() {
        setTitle("File Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());

        setLayout(new BorderLayout());

        // Panel for folder input and filter controls
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        // Text field for folder input
        folderInputField = new JTextField("J:\\Programming,J:\\Vidokursu_and_Books_From_118a");
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(folderInputField, gbc);

        checkBoxSortBySize = new JCheckBox("Sort by size and mark duplicates");
        checkBoxSortBySize.setSelected(false);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(checkBoxSortBySize, gbc);

        checkBoxCheckDurations = new JCheckBox("Check durations");
        checkBoxCheckDurations.setSelected(false);
        gbc.gridx = 2; // set the position to be right after the existing checkbox
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(checkBoxCheckDurations, gbc);

        // Text field for filter input
        filterInputField = new JTextField("*");
        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(filterInputField, gbc);

        // Filter button
        JButton filterButton = new JButton("Filter");
        filterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applyFilter();
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(filterButton, gbc);

        // Reset button
        JButton resetFilterButton = new JButton("Reset filter");
        resetFilterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                resetFilter();
            }
        });
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(resetFilterButton, gbc);

        add(inputPanel, BorderLayout.NORTH);

        // List to display files
        fileList = new JList<>();
        Font defaultFont = fileList.getFont();
        float smallerSize = defaultFont.getSize() * 1.05f; // 80% of default size
        fileList.setFont(defaultFont.deriveFont(smallerSize));

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    File selectedFile = fileList.getSelectedValue();
                    if (selectedFile != null) {
                        openFile(selectedFile);
                    }
                }
            }
        });
        addCellRendererToFileList(fileList);
        // Add list to scroll pane and then to center of frame
        JScrollPane scrollPane = new JScrollPane(fileList);
        add(scrollPane, BorderLayout.CENTER);

        // Action listener for folder input field: load files from directory/directories
        folderInputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadFiles();
            }
        });

        filterInputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyFilter();
            }
        });


        // Add menu items to the context menu
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> {
            // Get the selected file
            File selectedFile = fileList.getSelectedValue();

            if (selectedFile != null) {
                // Open the file
                openFile(selectedFile);
            }
        });
        contextMenu.add(openItem);

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> {
            // Get the selected file
            File selectedFile = fileList.getSelectedValue();

            if (selectedFile != null) {
                // Delete the file
                if (selectedFile.getName().contains(SPLITTER_METADATA)) {
                    File tempFile = new File(selectedFile.getAbsolutePath().substring(0,
                            selectedFile.getAbsolutePath().indexOf(SPLITTER_METADATA)));
                    deleteFile(tempFile);
                } else {
                    deleteFile(selectedFile);
                }

            }
        });
        contextMenu.add(deleteItem);

        JMenuItem openInExplorerItem = new JMenuItem("Open in Explorer");
        contextMenu.add(openInExplorerItem);
        openInExplorerItem.addActionListener(e -> {
            // Get the selected file
            File selectedFile = fileList.getSelectedValue();

            if (selectedFile != null) {
                // Open the file in the default file explorer
                try {
                    File tempFile = selectedFile;
                    if (tempFile.getName().contains(SPLITTER_METADATA)) {
                        tempFile = new File(tempFile.getAbsolutePath().substring(0, tempFile.getAbsolutePath().indexOf(SPLITTER_METADATA)));
                    }
                    //TODO: implement open in explorer with FOCUS on selected file
                    //Path parentPath = tempFile.toPath().getParent();
                    //Desktop.getDesktop().open(parentPath.toFile());
                    //String command = "cmd /c start /d \"" + tempFile.getAbsolutePath() + "\" \"" + tempFile.getAbsolutePath() + "\"";
                    //Runtime.getRuntime().exec(command);
                    String command = "explorer /select," + tempFile.getAbsolutePath() + "\"";
                    Runtime.getRuntime().exec(command);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        JMenuItem copyFilenameItem = new JMenuItem("Copy filename to clipboard");
        copyFilenameItem.addActionListener(e -> {
            // Get the selected file
            File selectedFile = fileList.getSelectedValue();
            if (selectedFile != null) {
                // Copy the filename to the clipboard
                String filename = selectedFile.getName();
                filename = filename.split(SPLITTER_METADATA)[0];
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(filename), null);
            }
        });
        contextMenu.add(copyFilenameItem);

        // Add the context menu to the JList
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    contextMenu.show(fileList, e.getX(), e.getY());
                }
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getKeyCode() == KeyEvent.VK_F && e.isControlDown() && e.getID() == KeyEvent.KEY_PRESSED) {
                filterInputField.requestFocusInWindow();
            }
            return false;
        });
    }

    private void deleteFile(File file) {
        try {
            Files.delete(file.toPath());
            loadFiles();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadFiles() {
        fileCount = 0;
        String[] paths = folderInputField.getText().split(",");
        allFiles = new LinkedList<>();

        for (String path : paths) {
            Path folderPath = Paths.get(path.trim());

            try {
                loadFilesRecursively(folderPath);
                writeMapToFile("c:\\tmp\\durations.txt", durations);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        try {
            FileOutputStream fileOut = new FileOutputStream("c:\\tmp\\folderFilesCount.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(folderFilesCount);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (checkBoxSortBySize.isSelected()) {
            allFiles.sort((o1, o2) -> {
                if (o1.getSizeInB() == o2.getSizeInB()) {
                    return 0;
                }
                if (o1.getSizeInB() > o2.getSizeInB()) {
                    return -1;
                } else {
                    return 1;
                }
            });
            for (int i = 1; i < allFiles.size(); i++) {
                //duplicates by size
                if (allFiles.get(i).getSizeInB() == allFiles.get(i - 1).getSizeInB()) {
                    allFiles.get(i - 1).setFile(new File(allFiles.get(i - 1).getFile().getAbsolutePath() + "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
                    allFiles.get(i).setFile(new File(allFiles.get(i).getFile().getAbsolutePath() + "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
                }

                //duplicates by name (NEED TO SEARCH ENTIRE)
//            if (equalIgnoringExtension(allFiles.get(i).getFile().getAbsolutePath(),
//                    allFiles.get(i-1).getFile().getAbsolutePath())  ) {
//                allFiles.get(i-1).setFile(new File(allFiles.get(i-1).getFile().getAbsolutePath()+ "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
//                allFiles.get(i).setFile(new File(allFiles.get(i).getFile().getAbsolutePath()+ "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
//            }
            }
            fileList.setListData(
                    allFiles.stream().filter(e -> e.getFile().getName().toLowerCase().contains("iiiiiiic"))
                            .map(e -> new File(e.getFile().getAbsolutePath() + "   --->>>>" + e.getSizeInB()))
                            .toArray(File[]::new));
        } else if(checkBoxCheckDurations.isSelected()){
            //todo: add checkbox , for now by default sort by duration
            allFiles.sort((o1, o2) -> {
                // Handle null durations first (place files with null duration at the end)
                if (o1.getDuration() == null && o2.getDuration() == null) {return 0;}
                else if (o1.getDuration() == null) {return 1;}
                else if (o2.getDuration() == null) { return -1; }
                // Reverse the comparison logic for descending order
                return o2.getDuration().compareTo(o1.getDuration());
            });
            fileList.setListData(
                    allFiles.stream().map(e -> new File(e.getFile().getAbsolutePath() + SPLITTER_METADATA + e.getSizeInB() + " " + e.getDurationInHoursMinutesSeconds() ))
                            .toArray(File[]::new));
        } else {
            fileList.setListData(
                    allFiles.stream().map(e -> new File(e.getFile().getAbsolutePath() + SPLITTER_METADATA + e.getSizeInB() + " " + e.getDurationInHoursMinutesSeconds() ))
                            .toArray(File[]::new));
        }

        System.err.println("FILES COUNt: " + fileList.getModel().getSize());
    }

    private void loadFilesRecursively(Path folderPath) throws IOException {

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if(folderFilesCount.get(entry.toString()) != null
                            && !folderContainsFolders(entry.toString()) ) {
//                        if(!checkBoxCheckCount.isSelected() || folderFilesCount.get(entry.toString()).size() == getNumberFilesInDir(entry.toString())) {
//                            allFiles.addAll(folderFilesCount.get(entry.toString()));
//                            System.err.println("Adding item from cache:" + entry);
//                            continue;
//                        } // TODO : disable cache nah
                    }
                    loadFilesRecursively(entry);

                    //serialize alfiles to file
//                    if(!folderContainsFolders(entry.toString())){
//                        folderFilesCount.put(entry.toString(), getAllFiles(entry.toString()));
//                    } //TODO: disable cache nah

                } else {
                    // Add regular files to the list
                    long sizeInBytes = Files.size(entry);
                    long sizeInB = sizeInBytes;

                    Integer videoDuration = 0;
                    if(checkBoxCheckDurations.isSelected()) {
                        videoDuration = durations.get(entry.toFile().getAbsolutePath());
                        if (videoDuration == null) {
                            videoDuration = getVideoDuration(entry.toFile().getAbsolutePath());
                        }
                        durations.put(entry.toFile().getAbsolutePath(), videoDuration);
                    }

                    FileInfo fileInfo = new FileInfo(entry.toFile(), sizeInB, videoDuration);

                    if (sizeInB > 566000) { // add only bigger than 566kb
                        allFiles.add(fileInfo);
                        fileCount++;
                        if (fileCount % 200 == 0) {
                            System.out.println("Adding item:" + fileInfo.getFile().getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Error while reading folder: " + folderPath.toString());
            ex.printStackTrace();
        }
    }

    private void applyFilter() {
        if (allFiles == null) return; // No files loaded yet

        String patternString = filterInputField.getText().toLowerCase();
        patternString = patternString.toLowerCase().replaceAll("\\*", ".*");  // Replace wildcard * with regex .*
        patternString = patternString.toLowerCase().replaceAll(" ", ".*");
        patternString = patternString.toLowerCase().replaceAll("\\+", ".+");
        Pattern pattern = Pattern.compile(patternString);

        java.util.List<FileInfo> filteredFiles = new ArrayList<>();
        for (FileInfo fileInfo : allFiles) {
            File file = fileInfo.getFile();
            Matcher matcher = pattern.matcher(file.getAbsolutePath().toLowerCase());
            if (matcher.find()) {
                filteredFiles.add(fileInfo);
            }
        }
        //fileList.setListData(filteredFiles.toArray(new File[0]));//old version, just filter
        //automatically sort by duration
        filteredFiles.sort((o1, o2) -> {
            if (o1.getDuration() == o2.getDuration()) {
                return 0;
            }
            if (o1.getDuration() > o2.getDuration()) {
                return -1;
            } else {
                return 1;
            }
        });

        fileList.setListData(
                filteredFiles.stream().map(e -> new File(e.getFile().getAbsolutePath() + SPLITTER_METADATA + e.getSizeInB() + " " + e.getDurationInHoursMinutesSeconds() ))
                        .toArray(File[]::new));
    }

    private void resetFilter() {
        fileList.setListData(allFiles.stream().map(FileInfo::getFile).toArray(File[]::new));
    }

    private void openFile(File file) {
        try {
            File tempToOpen = new File(file.getAbsolutePath().split("   --->>>>")[0]);
            Desktop.getDesktop().open(tempToOpen);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                FileExplorerMultipleFolders fileExplorer = new FileExplorerMultipleFolders();
                fileExplorer.setLocationRelativeTo(null);
                fileExplorer.setVisible(true);
            }
        });
    }

    public static boolean equalIgnoringExtension(String name1, String name2) {
        String name1NoExt = name1.substring(0, name1.lastIndexOf('.'));
        String name2NoExt = name2.substring(0, name2.lastIndexOf('.'));
        return name1NoExt.equals(name2NoExt);
    }

    class FileInfo implements Serializable {
        File file;
        long sizeInB;

        Integer duration;

        // Constructor and getters

        public FileInfo(File file, long sizeInB) {
            this.file = file;
            this.sizeInB = sizeInB;
        }

        public FileInfo(File file, long sizeInB, Integer duration) {
            this.file = file;
            this.sizeInB = sizeInB;
            this.duration = duration;
        }

        public String getDurationInHoursMinutesSeconds() {
            if (duration == null) {
                return "";
            }
            int hours = duration / 3600;
            int minutes = (duration % 3600) / 60;
            int seconds = duration % 60;
            return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
        }

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }

        public long getSizeInB() {
            return sizeInB;
        }

        public void setSizeInB(long sizeInB) {
            this.sizeInB = sizeInB;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }
    }


    private void addCellRendererToFileList(JList<File> fileList) {
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                File file = (File) value;
                String filename = file.getName();


                pconfig.forEach((k, v) -> {
                    if(!k.contains(",")){
                        if (filename.toLowerCase().contains(k)) {
                            component.setBackground(new Color(colorMapping.get(v)));
                            if(v.contains("black")) { //black
                                component.setForeground(Color.WHITE);
                            }
                        }
                    } else {
                        if (filename.toLowerCase().contains(k.split(",")[0])
                                && filename.toLowerCase().contains(k.split(",")[1])) {
                            component.setBackground(new Color(colorMapping.get(v)));
                        }
                    }
                });

//


                return component;
            }
        });
    }

    public static int getNumberFilesInDir(String path) {
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        int count = 0;
        for (File file : listOfFiles) {
            if (file.isFile()) {
                count++;
            }
        }
        return count;
    }

    public static boolean folderContainsFolders(String path) {
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isDirectory()) {
                return true;
            }
        }
        return false;
    }


    public static Integer getVideoDuration(String filePath) {
        // Check if file is less than 5MB
        File file = new File(filePath);
        long fileSizeInBytes = file.length();
        if (fileSizeInBytes < 5 * 1024 * 1024) {
            System.out.println("File is less than 10MB, skipping: " + filePath);
            return 0;
        }

        CustomFFMPEGLocator locator = new CustomFFMPEGLocator("C:\\Programs\\ffmpeg\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg.exe");

        try {
            MultimediaObject multimediaObject = new MultimediaObject(file, locator);
            MultimediaInfo info = multimediaObject.getInfo();
            long duration = info.getDuration() / 1000; // Duration in seconds
            System.out.println("Duration of the video is: " + filePath + " " + duration + " seconds");
            return (int) duration;
        } catch (Exception e) {
            System.err.println("Error during get duration " + filePath + ": " + e.getMessage());
        }
        return 0;
    }


    static  class CustomFFMPEGLocator extends ws.schild.jave.FFMPEGLocator {

        public CustomFFMPEGLocator(String ffmpegPath) {
            super();
        }

        @Override
        protected String getFFMPEGExecutablePath() {
            return "C:\\Programs\\ffmpeg\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg.exe";
        }
    }

    public static void writeMapToFile(String filePath, Map<String, Integer> durations) throws IOException {
        File file = new File(filePath);
        try (FileOutputStream fileOutputStream = new FileOutputStream(file);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(durations);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Integer> readMapFromFile(String filePath) throws IOException, ClassNotFoundException {
        File file = new File(filePath);
        try (FileInputStream fileInputStream = new FileInputStream(file);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            @SuppressWarnings("unchecked") // Handle unchecked cast warning
            Map<String, Integer> durations = (Map<String, Integer>) objectInputStream.readObject();
            return durations;
        } catch (IOException | ClassNotFoundException e) {
           e.printStackTrace();
        }
        return new HashMap<>();
    }


}