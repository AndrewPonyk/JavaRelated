package com.ap;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FileExplorerMultipleFolders extends JFrame {

    public static final String SPLITTER_METADATA = "   --->>>>";
    private JList<File> fileList;

    JPopupMenu contextMenu = new JPopupMenu();

    private JTextField folderInputField;

    private JCheckBox checkBoxSortBySize;
    private JTextField filterInputField;
    private Vector<FileInfo> allFiles;

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
        folderInputField = new JTextField("E:\\Programming,E:\\Vidokursu_and_Books_From_118a");
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(folderInputField, gbc);

        checkBoxSortBySize = new JCheckBox("Sort by size and mark duplicates");
        checkBoxSortBySize.setSelected(false);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(checkBoxSortBySize, gbc);


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
                if(selectedFile.getName().contains(SPLITTER_METADATA)) {
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
                    if(tempFile.getName().contains(SPLITTER_METADATA)) {
                        tempFile = new File(tempFile.getAbsolutePath().substring(0,tempFile.getAbsolutePath().indexOf(SPLITTER_METADATA)));
                    }
                    //TODO: implement open in explorer with FOCUS on selected file
                    //Path parentPath = tempFile.toPath().getParent();
                    //Desktop.getDesktop().open(parentPath.toFile());
                    //String command = "cmd /c start /d \"" + tempFile.getAbsolutePath() + "\" \"" + tempFile.getAbsolutePath() + "\"";
                    //Runtime.getRuntime().exec(command);
                    String command = "explorer /select," + tempFile.getAbsolutePath() +"\"";
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
        allFiles = new Vector<>();

        for (String path : paths) {
            Path folderPath = Paths.get(path.trim());

            try {
                loadFilesRecursively(folderPath);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }


        if (checkBoxSortBySize.isSelected()) {
            allFiles.sort((o1, o2) -> {
                if(o1.getSizeInB()==o2.getSizeInB()) {
                    return 0;
                }
                if(o1.getSizeInB()> o2.getSizeInB()) {
                    return -1;
                } else {
                    return 1;
                }
            });
            for (int i = 1; i < allFiles.size(); i++) {
                //duplicates by size
                if (allFiles.get(i).getSizeInB() == allFiles.get(i-1).getSizeInB()) {
                    allFiles.get(i-1).setFile(new File(allFiles.get(i-1).getFile().getAbsolutePath() + "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
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
                    allFiles.stream().filter(e->e.getFile().getName().toLowerCase().contains("iiiiiiic"))
                            .map(e->new File(e.getFile().getAbsolutePath()+ "   --->>>>" + e.getSizeInB()))
                            .toArray(File[]::new));
        } else {
                fileList.setListData(
                allFiles.stream().map(e->new File(e.getFile().getAbsolutePath()+ SPLITTER_METADATA + e.getSizeInB())).toArray(File[]::new));
        }

        System.err.println("FILES COUNt: " + fileList.getModel().getSize());
    }

    private void loadFilesRecursively(Path folderPath) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    // Recursively call this method on subfolders
                    loadFilesRecursively(entry);
                } else {
                    // Add regular files to the list
                    long sizeInBytes = Files.size(entry);
                    long sizeInB = sizeInBytes ;

                    FileInfo fileInfo = new FileInfo(entry.toFile(), sizeInB);
                    if(sizeInB > 399000) {
                        allFiles.add(fileInfo);
                        fileCount++;
                        if( fileCount % 50 == 0) {
                            System.out.println("Adding item:" + fileInfo.getFile().getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    private void applyFilter() {
        if (allFiles == null) return; // No files loaded yet

        String patternString = filterInputField.getText().toLowerCase();
        patternString = patternString.toLowerCase().replaceAll("\\*", ".*");  // Replace wildcard * with regex .*
        patternString = patternString.toLowerCase().replaceAll(" ", ".*");
        Pattern pattern = Pattern.compile(patternString);

        java.util.List<File> filteredFiles = new ArrayList<>();
        for (FileInfo fileInfo : allFiles) {
            File file = fileInfo.getFile();
            Matcher matcher = pattern.matcher(file.getAbsolutePath().toLowerCase());
            if (matcher.find()) {
                filteredFiles.add(file);
            }
        }
        fileList.setListData(filteredFiles.toArray(new File[0]));
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

    class FileInfo {
        File file;
        long sizeInB;

        // Constructor and getters

        public FileInfo(File file, long sizeInB) {
            this.file = file;
            this.sizeInB = sizeInB;
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
    }
}