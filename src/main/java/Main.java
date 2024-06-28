import documentTokenizing.FileProcessor;
import documentTokenizing.StopWordsLoader;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {
        final String outputPath = "src/main/resources/output";
        final String inputFilePath = "src/main/resources/smol_corpus";//todo make into cli

        //Part 1: Tokenizing Documents
        Set<String> stopWords = StopWordsLoader.loadStopWords("src/main/resources/stopped_words.txt");
        FileProcessor processor = new FileProcessor(stopWords);
        ensureOutputDirectoryExists(outputPath);
        processor.processFiles(inputFilePath, outputPath);

        //Part 2: Inverting the index

        //Part 3: Reading the index
    }



    private static void ensureOutputDirectoryExists(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                System.out.println("Created the directory: " + path);
            } else {
                System.err.println("Failed to create the directory: " + path);
                System.exit(1);  // Exit if the directory cannot be created to avoid further errors
            }
        } else {
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
