package com.ap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class FileExplorerMultipleFolders extends JFrame {

    private JList<File> fileList;
    private JTextField folderInputField;
    private JTextField filterInputField;
    private Vector<FileInfo> allFiles;

    public FileExplorerMultipleFolders() {
        setTitle("File Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(new BorderLayout());

        // Panel for folder input and filter controls
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        // Text field for folder input
        folderInputField = new JTextField("c:\\tmp");
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(folderInputField, gbc);

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
        float smallerSize = defaultFont.getSize() * 0.75f; // 80% of default size
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

        loadFiles();
    }

    private void loadFiles() {
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

        allFiles.sort((o1, o2) -> {
            if(o1.getSizeInKB()==o2.getSizeInKB()){
                return 0;
            }
            if(o1.getSizeInKB()> o2.getSizeInKB()) {
                return -1;
            } else {
                return 1;
            }
        });
        for (int i = 1; i < allFiles.size(); i++) {
            if (allFiles.get(i).getSizeInKB() == allFiles.get(i-1).getSizeInKB()) {
                allFiles.get(i-1).setFile(new File(allFiles.get(i-1).getFile().getAbsolutePath()+ "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
                allFiles.get(i).setFile(new File(allFiles.get(i).getFile().getAbsolutePath()+ "   --->>>>    DUPLIIIIIIIIIIIIIIIIIIIIIIICATEEEEEEEEEEE "));
            }
        }

        //fileList.setListData(
                //        allFiles.stream().filter(e->e.getFile().getName().toLowerCase().contains("iiiiiiic")).map(e->new File(e.getFile().getAbsolutePath()+ "   --->>>>" + e.getSizeInKB())).toArray(File[]::new));

        fileList.setListData(
                allFiles.stream().map(e->new File(e.getFile().getAbsolutePath()+ "   --->>>>" + e.getSizeInKB())).toArray(File[]::new));
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
                    long sizeInKB = sizeInBytes / 1024;

                    FileInfo fileInfo = new FileInfo(entry.toFile(), sizeInKB);
                    allFiles.add(fileInfo);
                }
            }
        }
    }

    private void applyFilter() {
        if (allFiles == null) return; // No files loaded yet

        String patternString = filterInputField.getText().toLowerCase();
        patternString = patternString.toLowerCase().replaceAll("\\*", ".*");  // Replace wildcard * with regex .*
        Pattern pattern = Pattern.compile(patternString);

        Vector<File> filteredFiles = new Vector<>();
        for (File file : allFiles.stream().map(FileInfo::getFile).toArray(File[]::new)) {
            Matcher matcher = pattern.matcher(file.getAbsolutePath().toLowerCase());
            if (matcher.find()) {
                filteredFiles.add(file);
            }
        }
        fileList.setListData(filteredFiles);
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

    class FileInfo {
        File file;
        long sizeInKB;

        // Constructor and getters

        public FileInfo(File file, long sizeInKB) {
            this.file = file;
            this.sizeInKB = sizeInKB;
        }

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }

        public long getSizeInKB() {
            return sizeInKB;
        }

        public void setSizeInKB(long sizeInKB) {
            this.sizeInKB = sizeInKB;
        }
    }
}