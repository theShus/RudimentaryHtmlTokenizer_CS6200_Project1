package fileProcessing;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
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


    public void processFiles(String directoryPath, String outputPath) throws IOException {
        List<File> files = listFiles(directoryPath);
        for (File file : files) {
            processDocument(file);
        }
        finalizeIndexing(outputPath);
    }

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

    private List<File> listFiles(String directoryPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            return paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    private String extractText(File file) throws IOException { //1.2
        String fileContent = Files.readString(file.toPath());
        // Find the beginning of the HTML content
        int htmlStartIndex = fileContent.indexOf("<!DOCTYPE");
        if (htmlStartIndex == -1) {
            htmlStartIndex = fileContent.indexOf("<html");  // Some documents might not have DOCTYPE
        }
        if (htmlStartIndex == -1) return null;  // Return null if no HTML tag is found

        String htmlContent = fileContent.substring(htmlStartIndex);
        Document document = Jsoup.parse(htmlContent);
        return document.text();  // Extracts and returns just the text from the HTML, ignoring all tags
    }

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
    private void finalizeIndexing(String outputPath) throws IOException {
        writeDocIds(outputPath);
        writeTermIds(outputPath);
        writeDocIndex(outputPath);
    }

    private void writeDocIds(String outputPath) throws IOException {
        TreeMap<Integer, String> sortedDocs = new TreeMap<>(docIdMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath + "/docids.txt"))) {
            for (Map.Entry<Integer, String> entry : sortedDocs.entrySet()) {
                writer.write(entry.getKey() + "\t" + entry.getValue() + "\n");
            }
        }
    }

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

    private void writeDocIndex(String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath + "/doc_index.txt"))) {
            for (Map.Entry<Integer, Map<Integer, List<Integer>>> docEntry : docIndex.entrySet()) {
                int docId = docEntry.getKey();
                Map<Integer, List<Integer>> terms = docEntry.getValue();
                for (Map.Entry<Integer, List<Integer>> termEntry : terms.entrySet()) {
                    int termId = termEntry.getKey();
                    List<Integer> positions = termEntry.getValue();

                    // Creating the positions list string
                    String positionsList = positions.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(", ", "[", "]"));

                    // Construct the line with a fixed number of spaces for alignment
                    String line = String.format("id: %d    t_termid: %d    positions: %s%n", docId, termId, positionsList);

                    writer.write(line);
                }
            }
        }
    }

}
