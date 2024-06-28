import documentTokenizing.FileProcessor;
import documentTokenizing.StopWordsLoader;
import indexInverting.IndexInverter;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {

        //Part 1: Tokenizing Documents
        final String outputPath1 = "src/main/resources/output_1";
        final String inputFilePath = "src/main/resources/smol_corpus";//todo make into cli
        final String stopWordsPath = "src/main/resources/stopped_words.txt";

        Set<String> stopWords = StopWordsLoader.loadStopWords(stopWordsPath);
        FileProcessor processor = new FileProcessor(stopWords);
        ensureOutputDirectoryExists(outputPath1);
        processor.processFiles(inputFilePath, outputPath1);


        //Part 2: Inverting the index
        final String docIndexPath = outputPath1 + "/doc_index.txt";
        final String outputPath2 = "src/main/resources/output_2";
        ensureOutputDirectoryExists(outputPath2);
        final String termIndexPath = outputPath2 + "/term_index.txt";
        final String termInfoPath = outputPath2 + "/term_info.txt";

        IndexInverter inverter = new IndexInverter();
        inverter.buildInvertedIndex(docIndexPath);
        inverter.writeInvertedIndex(termIndexPath, termInfoPath);



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
