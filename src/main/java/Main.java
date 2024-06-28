import documentTokenizing.FileProcessor;
import documentTokenizing.StopWordsLoader;
import indexInverting.IndexInverter;
import indexReading.IndexReader;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        // Ask for the input file path (corpus directory)
        System.out.println("Enter the corpus directory path:");
        String inputFilePath = scanner.nextLine();

        if (inputFilePath == null || inputFilePath.isEmpty()) {
            System.out.println("Input file path is required. Usage: java Main --input <inputFilePath>");
            System.exit(1);
        }


        // Part 1: Tokenizing Documents
        final String outputPath1 = "src/main/resources/output_1";
        final String stopWordsPath = "src/main/resources/stopped_words.txt";
        ensureOutputDirectoryExists(outputPath1);

        Set<String> stopWords = StopWordsLoader.loadStopWords(stopWordsPath);
        FileProcessor processor = new FileProcessor(stopWords);
        processor.processFiles(inputFilePath, outputPath1);

        // Part 2: Inverting the index
        final String docIndexPath = outputPath1 + "/doc_index.txt";
        final String outputPath2 = "src/main/resources/output_2";
        ensureOutputDirectoryExists(outputPath2);
        final String termIndexPath = outputPath2 + "/term_index.txt";
        final String termInfoPath = outputPath2 + "/term_info.txt";

        IndexInverter inverter = new IndexInverter();
        inverter.buildInvertedIndex(docIndexPath);
        inverter.writeInvertedIndex(termIndexPath, termInfoPath);

        // Part 3: Reading the index
        String docIdsPath = outputPath1 + "/docids.txt";
        String termIdsPath = outputPath1 + "/termids.txt";
        IndexReader reader = new IndexReader(docIdsPath, termIdsPath, termInfoPath, termIndexPath, docIndexPath);

        while (true) {
            System.out.println("\nEnter command: (--doc DOCNAME || --term TERM || --term TERM --doc DOCNAME || exit)");
            String command = scanner.nextLine();

            if (command.equals("exit")) {
                break;
            }

            String[] commandParts = command.split(" ");
            if (commandParts.length == 2 && commandParts[0].equals("--doc")) {
                String docName = commandParts[1];
                reader.printDocInfo(docName);
            }
            else if (commandParts.length == 2 && commandParts[0].equals("--term")) {
                String term = commandParts[1];
                reader.printTermInfo(term);
            }
            else if (commandParts.length == 4 && commandParts[0].equals("--term") && commandParts[2].equals("--doc")) {
                String term = commandParts[1];
                String docName = commandParts[3];
                reader.printTermDocInfo(term, docName);
            } else {
                System.out.println("Invalid command. Usage: --doc DOCNAME | --term TERM [--doc DOCNAME] | exit");
            }
        }

        scanner.close();
    }



    private static void ensureOutputDirectoryExists(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                System.out.println("Created the directory: " + path);
            }
            else {
                System.err.println("Failed to create the directory: " + path);
                System.exit(1);  // Exit if the directory cannot be created to avoid further errors
            }
        }
        else {
            // Directory exists, clear existing files
            for (File file : directory.listFiles()) {
                if (!file.delete()) {
                    System.err.println("Failed to delete existing file: " + file.getPath());
                    System.exit(1);  // Exit if files cannot be deleted to avoid further errors
                }
            }
        }
    }
}
