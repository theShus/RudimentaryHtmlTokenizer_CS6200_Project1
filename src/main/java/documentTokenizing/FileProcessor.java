package documentTokenizing;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileProcessor {

    // Compile the regular expression pattern for tokenization
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\w+(\\.?\\w+)*");
    private final Set<String> stopWords;

    private final Map<String, Integer> docIdMap = new HashMap<>();
    private final Map<String, Integer> termIdMap = new HashMap<>();
    private final Map<Integer, Map<Integer, List<Integer>>> docIndex = new HashMap<>();
    private int docIdCounter = 1;
    private int termIdCounter = 1;

    public FileProcessor(Set<String> stopWords) {
        this.stopWords = stopWords;
    }


    /**
     Processes all files in the given directory, tokenizes their content, and writes the resulting indices to the output path.
     */
    public void processFiles(String directoryPath, String outputPath) throws IOException {
        List<File> files = listFiles(directoryPath);
        int totalFiles = files.size();
        int processedFiles = 0;

        for (File file : files) {
            processDocument(file);
            processedFiles++;
            printProgress(processedFiles, totalFiles);
        }
        finalizeIndexing(outputPath);
        System.out.println();  // Move to the next line after completion
    }

    private void printProgress(int processedFiles, int totalFiles) {
        System.out.print("\rProcessing files: " + processedFiles + "/" + totalFiles);
        System.out.flush();
    }


    /**
     Reads and processes a single document file, extracting text, tokenizing, and updating indices.
     */
    private void processDocument(File file) throws IOException {
        String content = extractText(file);
        if (content == null) {
            System.err.println("No HTML content found in file: " + file.getName());
            return;
        }
        List<String> tokens = tokenizeAndStem(content);

        int docId = docIdMap.computeIfAbsent(file.getName(), k -> docIdCounter++);
        Map<Integer, List<Integer>> termPositions = docIndex.computeIfAbsent(docId, k -> new HashMap<>());

        for (int i = 0; i < tokens.size(); i++) {
            int termId = termIdMap.computeIfAbsent(tokens.get(i), k -> termIdCounter++);
            termPositions.computeIfAbsent(termId, k -> new ArrayList<>()).add(i + 1);
        }
    }

    /**
     Lists all regular files in the specified directory and returns them as a list.
     */
    private List<File> listFiles(String directoryPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            return paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     Extracts and returns the plain text content from the given HTML document file, handling different character encodings.
     */
    private String extractText(File file) throws IOException {//1.2
        // Try with UTF-8 encoding first
        try {
            return extractTextWithEncoding(file, StandardCharsets.UTF_8);
        }
        catch (MalformedInputException e) {
//            System.err.println("MalformedInputException with UTF-8, trying ISO-8859-1 for file: " + file.getName());
            // Try with ISO-8859-1 encoding
            try {
                return extractTextWithEncoding(file, StandardCharsets.ISO_8859_1);
            }
            catch (MalformedInputException e1) {
//                System.err.println("MalformedInputException with ISO-8859-1 for file: " + file.getName());
                // Try with default charset as a last resort
                try {
                    return extractTextWithEncoding(file, Charset.defaultCharset());
                }
                catch (MalformedInputException e2) {
                    System.err.println("MalformedInputException with default charset for file: " + file.getName());
                    return null;
                }
            }
        }
    }

    /**
     Reads the file content with the specified charset, extracts HTML text, or returns plain text if no HTML is found.
     */
    private String extractTextWithEncoding(File file, Charset charset) throws IOException {
        String fileContent = Files.readString(file.toPath(), charset);
        // Find the beginning of the HTML content
        int htmlStartIndex = fileContent.indexOf("<!DOCTYPE");
        if (htmlStartIndex == -1) {
            htmlStartIndex = fileContent.indexOf("<html");  // Some documents might not have DOCTYPE
        }
        if (htmlStartIndex == -1) {
            htmlStartIndex = fileContent.indexOf("<");  // Look for any HTML tag
        }

        if (htmlStartIndex != -1) {
            String htmlContent = fileContent.substring(htmlStartIndex);
            Document document = Jsoup.parse(htmlContent);
            return document.text();  // Extracts and returns just the text from the HTML, ignoring all tags
        }
        else {
            // If no HTML content is found, return the plain text content after skipping headers
            int headersEndIndex = fileContent.indexOf("\n\n"); // Assuming headers end with a double newline
            if (headersEndIndex != -1) {
                return fileContent.substring(headersEndIndex).trim();
            } else {
                return fileContent.trim();  // If no headers are found, return the entire content as plain text
            }
        }
    }


    /**
     Tokenizes the input text and applies stemming, returning a list of stemmed tokens.
     */
    private List<String> tokenizeAndStem(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text); //1.3 tokenize
        while (matcher.find()) {
            String token = matcher.group().toLowerCase();  //1.4 Convert each token to lowercase
            if (!this.stopWords.contains(token)) { //1.5 don't use stop words
                tokens.add(token);
            }
        }
        return stemTokens(tokens);  //1.6 Apply stemming to the filtered tokens
    }

    // 1.6 stemming the tokens
    /**
     Applies the Porter stemmer to a list of tokens and returns the list of stemmed tokens.
     */
    private List<String> stemTokens(List<String> tokens) {
        List<String> stemmedTokens = new ArrayList<>();
        StandardTokenizer tokenizer = new StandardTokenizer();
        tokenizer.setReader(new StringReader(String.join(" ", tokens)));
        TokenStream tokenStream = new PorterStemFilter(tokenizer);
        CharTermAttribute charTermAttr = tokenStream.addAttribute(CharTermAttribute.class);
        try {
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                stemmedTokens.add(charTermAttr.toString());
            }
            tokenStream.end();
            tokenStream.close();
        } catch (IOException e) {
            System.err.println("Error during stemming: " + e.getMessage());
        }
        return stemmedTokens;
    }

    //1.7 write documents
    /**
     Writes the final indices (document IDs, term IDs, and index) to the specified output path.
     */
    private void finalizeIndexing(String outputPath) throws IOException {
        writeDocIds(outputPath);
        writeTermIds(outputPath);
        writeDocIndex(outputPath);
    }

    /**
     Writes the mapping of document IDs to document names to a file in the output path.
     */
    private void writeDocIds(String outputPath) throws IOException {
        TreeMap<Integer, String> sortedDocs = new TreeMap<>(docIdMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath + "/docids.txt"))) {
            for (Map.Entry<Integer, String> entry : sortedDocs.entrySet()) {
                writer.write(entry.getKey() + "\t" + entry.getValue() + "\n");
            }
        }
    }

    /**
     Writes the mapping of term IDs to terms to a file in the output path.
     */
    private void writeTermIds(String outputPath) throws IOException {
        TreeMap<Integer, String> sortedTerms = new TreeMap<>(termIdMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath + "/termids.txt"))) {
            for (Map.Entry<Integer, String> entry : sortedTerms.entrySet()) {
                String formattedTermId = String.format("%-4d", entry.getKey()); // Ensure a minimum width
                writer.write(formattedTermId + "\t" + entry.getValue() + "\n");
            }
        }
    }

    /**
     Writes the index of documents and their term positions to a file in the output path.
     */
    private void writeDocIndex(String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath + "/doc_index.txt"))) {
            for (Map.Entry<Integer, Map<Integer, List<Integer>>> docEntry : docIndex.entrySet()) {
                int docId = docEntry.getKey();
                Map<Integer, List<Integer>> terms = docEntry.getValue();
                for (Map.Entry<Integer, List<Integer>> termEntry : terms.entrySet()) {
                    int termId = termEntry.getKey();
                    List<Integer> positions = termEntry.getValue();

                    // Creating the positions list string without any delimiter
                    String positionsList = positions.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(" "));

                    // Construct the line without text labels and commas
                    String line = String.format("%d\t%d\t%s%n", docId, termId, positionsList);

                    writer.write(line);
                }
            }
        }
    }


}
