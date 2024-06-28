package indexReading;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.en.PorterStemFilter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class IndexReader {

    private final Map<String, Integer> docIdMap = new HashMap<>();
    private final Map<Integer, String> termIdMap = new HashMap<>();
    private final Map<Integer, TermInfo> termInfoMap = new HashMap<>();
    private final Map<Integer, Map<Integer, List<Integer>>> docIndex = new HashMap<>();
    private final String termIndexPath;

    public IndexReader(String docIdsPath, String termIdsPath, String termInfoPath, String termIndexPath, String docIndexPath) throws IOException {
        loadDocIds(docIdsPath);
        loadTermIds(termIdsPath);
        loadTermInfo(termInfoPath);
        loadDocIndex(docIndexPath);
        this.termIndexPath = termIndexPath;
    }

    /**
     Reads the docIdsPath file and populates the docIdMap with document names and their corresponding IDs.
     */
    private void loadDocIds(String docIdsPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(docIdsPath));
        for (String line : lines) {
            String[] parts = line.split("\\t");
            int docId;
            try {
                docId = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Skipping malformed doc ID in line: " + line);
                continue;
            }
            String docName = parts[1].trim();
            docIdMap.put(docName, docId);
        }
    }

    /**
     Reads the termIdsPath file and fills termIdMap with term IDs and the actual terms.
     */
    private void loadTermIds(String termIdsPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(termIdsPath));
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue; // Skip empty lines
            }
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                System.err.println("Skipping malformed line: " + line);
                continue;
            }
            int termId;
            try {
                termId = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Skipping malformed term ID in line: " + line);
                continue;
            }
            String term = parts[1].trim();
            termIdMap.put(termId, term);
        }
    }

    /**
     * Reads the termInfoPath file to get the details of each term and stores them in termInfoMap.
     */
    private void loadTermInfo(String termInfoPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(termInfoPath));
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue; // Skip empty lines
            }
            String[] parts = line.split("\\t");
            if (parts.length < 4) {
                System.err.println("Skipping malformed line: " + line);
                continue;
            }
            int termId;
            long offset;
            int totalOccurrences;
            int docCount;
            try {
                termId = Integer.parseInt(parts[0].trim());
                offset = Long.parseLong(parts[1].trim());
                totalOccurrences = Integer.parseInt(parts[2].trim());
                docCount = Integer.parseInt(parts[3].trim());
            } catch (NumberFormatException e) {
                System.err.println("Skipping malformed numbers in line: " + line);
                continue;
            }
            termInfoMap.put(termId, new TermInfo(offset, totalOccurrences, docCount));
        }
    }

    /**
     Reads the docIndexPath file and builds the docIndex, which maps document IDs to term positions.
     */
    private void loadDocIndex(String docIndexPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(docIndexPath));
        for (String line : lines) {
            String[] parts = line.split("\t");
            int docId;
            int termId;
            try {
                docId = Integer.parseInt(parts[0].trim());
                termId = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("Skipping malformed doc ID or term ID in line: " + line);
                continue;
            }
            List<Integer> positions;
            try {
                positions = Arrays.stream(parts[2].split(" "))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                System.err.println("Skipping malformed positions in line: " + line);
                continue;
            }
            docIndex.computeIfAbsent(docId, k -> new HashMap<>())
                    .computeIfAbsent(termId, k -> new ArrayList<>())
                    .addAll(positions);
        }
    }

    /**
     Prints information about a document, including its ID, number of distinct terms, and total number of terms.
     */
    public void printDocInfo(String docName) {
        Integer docId = docIdMap.get(docName);
        if (docId == null) {
            System.err.println("Document not found: " + docName);
            return;
        }

        Map<Integer, List<Integer>> termPositions = docIndex.get(docId);
        if (termPositions == null) {
            System.err.println("No term positions found for document ID: " + docId);
            return;
        }

        int distinctTerms = termPositions.size();
        int totalTerms = termPositions.values().stream().mapToInt(List::size).sum();

        System.out.println("Listing for document: " + docName);
        System.out.println("DOCID: " + docId);
        System.out.println("Distinct terms: " + distinctTerms);
        System.out.println("Total terms: " + totalTerms);
    }

    /**
     Prints information about a term, including its ID, document frequency, term frequency, and offset in the inverted list.
     */
    public void printTermInfo(String term) throws IOException {
        int termId;
        try {
            termId = stemAndGetTermId(term);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }
        TermInfo termInfo = termInfoMap.get(termId);
        if (termInfo == null) {
            System.err.println("Term not found: " + term);
            return;
        }

        System.out.println("Listing for term: " + term);
        System.out.println("TERMID: " + termId);
        System.out.println("Number of documents containing term: " + termInfo.docCount);
        System.out.println("Term frequency in corpus: " + termInfo.totalOccurrences);
        System.out.println("Inverted list offset: " + termInfo.offset);
    }

    /**
     Prints the positions of a term within a specific document, including term ID, document ID, and positions.
     */
    public void printTermDocInfo(String term, String docName) throws IOException {
        int termId;

        try {
            termId = stemAndGetTermId(term);
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }
        TermInfo termInfo = termInfoMap.get(termId);
        if (termInfo == null) {
            System.err.println("Term not found: " + term);
            return;
        }

        Integer docId = docIdMap.get(docName);
        if (docId == null) {
            System.err.println("Document not found: " + docName);
            return;
        }

        List<String> invertedList;
        try {
            invertedList = getInvertedList(termInfo.offset);
        }
        catch (IOException e) {
            System.err.println("Failed to read inverted list for term: " + term);
            return;
        }
        if (invertedList == null) {
            System.err.println("Inverted list not found for term: " + term);
            return;
        }

        for (String entry : invertedList) {
            String[] parts = entry.split(":");
            int entryDocId;
            try {
                entryDocId = Integer.parseInt(parts[0]);
            }
            catch (NumberFormatException e) {
                System.err.println("Skipping malformed entry doc ID in entry: " + entry);
                continue;
            }
            if (entryDocId == docId) {
                String[] positions = parts[1].split("\\t");
                System.out.println("Inverted list for term: " + term);
                System.out.println("In document: " + docName);
                System.out.println("TERMID: " + termId);
                System.out.println("DOCID: " + docId);
                System.out.println("Term frequency in document: " + positions.length);
                System.out.println("Positions: " + String.join(", ", positions));
                return;
            }
        }

        System.err.println("Term not found in document: " + docName);
    }

    /**
     Stems the input term and retrieves its corresponding term ID from termIdMap.
     */
    private int stemAndGetTermId(String term) throws IOException {
        List<String> tokens = tokenizeAndStem(term);
        if (tokens.isEmpty()) {
            System.err.println("Unable to stem term: " + term);
            return -1; // Indicate an error
        }
        return tokens.stream()
                .map(token -> termIdMap.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(token))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(-1))
                .findFirst()
                .orElse(-1);
    }

    /**
     Tokenizes and stems the input text, returning a list of stemmed tokens.
     */
    private List<String> tokenizeAndStem(String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        StandardTokenizer tokenizer = new StandardTokenizer();
        tokenizer.setReader(new StringReader(text));
        TokenStream tokenStream = new PorterStemFilter(tokenizer);
        CharTermAttribute charTermAttr = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            tokens.add(charTermAttr.toString());
        }
        tokenStream.end();
        tokenStream.close();
        return tokens;
    }

    /**
     Reads the inverted list for a term from termIndexPath, starting at the given offset.
     */
    private List<String> getInvertedList(long offset) throws IOException {
        RandomAccessFile file = new RandomAccessFile(termIndexPath, "r");
        file.seek(offset);
        String line = file.readLine();
        file.close();
        if (line == null) {
            return null;
        }
        return Arrays.asList(line.split("\t"));
    }

}
