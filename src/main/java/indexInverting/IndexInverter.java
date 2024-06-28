package indexInverting;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class IndexInverter {

    // invertedIndex: Maps TERMID to a map of DOCID to a list of positions within the document
    // Example: { 1: { 100: [5, 15, 25], 101: [2, 18] }, 2: { 100: [3, 8, 12] } }
    private final Map<Integer, Map<Integer, List<Integer>>> invertedIndex = new HashMap<>();

    // termOffsets: Maps TERMID to the byte offset of its entry in term_index.txt
    // Example: { 1: 0L, 2: 150L }
    private final Map<Integer, Long> termOffsets = new HashMap<>();

    // termFrequencies: Maps TERMID to the total number of occurrences of the term in the entire corpus
    // Example: { 1: 50, 2: 35 }
    private final Map<Integer, Integer> termFrequencies = new HashMap<>();

    // docFrequencies: Maps TERMID to the number of documents in which the term appears
    // Example: { 1: 10, 2: 8 }
    private final Map<Integer, Integer> docFrequencies = new HashMap<>();



    public void buildInvertedIndex(String docIndexPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(docIndexPath));
        for (String line : lines) {
            String[] parts = line.split("\\t");

            int docId = Integer.parseInt(parts[0]);
            int termId = Integer.parseInt(parts[1]);
            String[] positions = parts[2].split(" ");

            List<Integer> posList = new ArrayList<>();
            for (String pos : positions) {
                posList.add(Integer.parseInt(pos));
            }


            if (!invertedIndex.containsKey(termId)) {
                invertedIndex.put(termId, new HashMap<>());
            }
            Map<Integer, List<Integer>> docMap = invertedIndex.get(termId);

            if (!docMap.containsKey(docId)) {
                docMap.put(docId, new ArrayList<>());
            }
            List<Integer> positionsList = docMap.get(docId);

            positionsList.addAll(posList);
        }
    }

    public void writeInvertedIndex(String termIndexPath, String termInfoPath) throws IOException {
        try (BufferedWriter termIndexWriter = new BufferedWriter(new FileWriter(termIndexPath));
             BufferedWriter termInfoWriter = new BufferedWriter(new FileWriter(termInfoPath))) {
            long offset = 0;

            for (Map.Entry<Integer, Map<Integer, List<Integer>>> entry : invertedIndex.entrySet()) {
                int termId = entry.getKey();
                StringBuilder indexLine = new StringBuilder(termId + "\t");
                int totalOccurrences = 0;
                int docCount = entry.getValue().size();

                List<Integer> docIds = new ArrayList<>(entry.getValue().keySet());
                Collections.sort(docIds);
                Integer lastDocId = null;

                for (Integer docId : docIds) {
                    List<Integer> positions = entry.getValue().get(docId);
                    Collections.sort(positions);
                    Integer lastPos = null;

                    for (Integer pos : positions) {
                        if (lastDocId != null) {
                            indexLine.append((docId - lastDocId) + ":");
                            indexLine.append((pos - (lastPos == null ? 0 : lastPos)) + "\t");
                        } else {
                            indexLine.append(docId + ":" + pos + "\t");
                        }
                        lastDocId = docId;
                        lastPos = pos;
                        totalOccurrences++;
                    }
                }

                // Write term_index.txt
                String indexLineStr = indexLine.toString().trim() + "\n";
                termIndexWriter.write(indexLineStr);

                // Track offset and metadata for term_info.txt
                termOffsets.put(termId, offset);
                termFrequencies.put(termId, totalOccurrences);
                docFrequencies.put(termId, docCount);
                offset += indexLineStr.getBytes().length;
            }

            // Write term_info.txt
            for (Integer termId : termOffsets.keySet()) {
                String infoLine = termId + "\t" +
                        termOffsets.get(termId) + "\t" +
                        termFrequencies.get(termId) + "\t" +
                        docFrequencies.get(termId) + "\n";
                termInfoWriter.write(infoLine);
            }
        }
    }
}