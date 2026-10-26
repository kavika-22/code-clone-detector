package org.revolt.clonedetector;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private record MethodInfo(String file, String method, List<String> tokens) {}

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args.length > 0 ? args[0] : "src/test/resources/sample");
        double threshold = args.length > 1 ? Double.parseDouble(args[1]) : 0.50;
        if (!Files.exists(input)) {
            System.err.println("Input path does not exist: " + input.toAbsolutePath());
            System.exit(1);
        }

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(input)) {
            javaFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted().collect(Collectors.toList());
        }

        List<MethodInfo> methods = new ArrayList<>();
        for (Path file : javaFiles) {
            try {
                CompilationUnit unit = StaticJavaParser.parse(file);
                for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                    String normalized = normalize(method.clone().toString());
                    List<String> tokens = tokenize(normalized);
                    if (!tokens.isEmpty()) {
                        methods.add(new MethodInfo(file.toString(), method.getNameAsString(), tokens));
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not parse " + file + ": " + e.getMessage());
            }
        }

        Path report = Path.of("clone-report.csv");
        StringBuilder csv = new StringBuilder("file_a,method_a,file_b,method_b,similarity\n");
        int matches = 0;
        for (int i = 0; i < methods.size(); i++) {
            for (int j = i + 1; j < methods.size(); j++) {
                MethodInfo a = methods.get(i), b = methods.get(j);
                double score = jaccard(a.tokens(), b.tokens());
                if (score >= threshold) {
                    matches++;
                    System.out.printf(Locale.ROOT, "%.1f%% similar: %s::%s <-> %s::%s%n",
                            score * 100, a.file(), a.method(), b.file(), b.method());
                    csv.append(csv(a.file())).append(',').append(csv(a.method())).append(',')
                            .append(csv(b.file())).append(',').append(csv(b.method())).append(',')
                            .append(String.format(Locale.ROOT, "%.4f", score)).append('\n');
                }
            }
        }
        Files.writeString(report, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("Scanned " + javaFiles.size() + " Java file(s), found " + methods.size()
                + " method(s), reported " + matches + " pair(s). Report: " + report.toAbsolutePath());
        if (methods.size() < 2) System.out.println("Add at least two Java methods to compare.");
    }

    // This starter normalization intentionally abstracts variable/name differences.
    private static String normalize(String source) {
        String noComments = source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
        CompilationUnit unit = StaticJavaParser.parse("class Temporary { " + noComments + " }");
        unit.accept(new ModifierVisitor<Void>() {
            @Override public Visitable visit(NameExpr n, Void arg) {
                return super.visit(n, arg);
            }
        }, null);
        return noComments.replaceAll("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b", "IDENT")
                .replaceAll("\\s+", " ").trim();
    }

    private static List<String> tokenize(String code) {
        List<String> result = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*|\\d+(?:\\.\\d+)?|==|!=|<=|>=|&&|\\|\\||[^\\s]").matcher(code);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static double jaccard(List<String> a, List<String> b) {
        Set<String> left = new HashSet<>(a), right = new HashSet<>(b);
        Set<String> intersection = new HashSet<>(left); intersection.retainAll(right);
        Set<String> union = new HashSet<>(left); union.addAll(right);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
