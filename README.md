# Code Clone and Hidden Duplication Detector (Starter Prototype)

A small Java/Maven starter project that scans Java source files, extracts methods, normalizes identifiers and whitespace, compares token sets with Jaccard similarity, and writes matching method pairs to `clone-report.csv`.

## Requirements
- JDK 17 or newer
- Maven 3.8+
- VS Code with the Extension Pack for Java (optional but recommended)

## Run
From the project folder:

```bash
mvn clean compile
mvn exec:java -Dexec.args="src/test/resources/sample 0.50"
```

Arguments are: `<folder-containing-java-files> <similarity-threshold>`. The threshold is between `0` and `1`; `0.50` means 50% similarity.

The program prints matching method pairs and creates `clone-report.csv` in the project folder.

## Important limitations
This is an educational prototype, not a production-grade detector. It uses a simple token-set Jaccard score, so it can miss some clones and can report false positives. Identifier normalization is deliberately basic and does not fully distinguish variable names from method names or types. Review results before using them as evidence.
