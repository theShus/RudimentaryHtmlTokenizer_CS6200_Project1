package indexReading;

public class TermInfo {
    long offset;
    int totalOccurrences;
    int docCount;

    TermInfo(long offset, int totalOccurrences, int docCount) {
        this.offset = offset;
        this.totalOccurrences = totalOccurrences;
        this.docCount = docCount;
    }
}
