import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class AssignmentTracker {

    private static final String DATA_FILE = "assignments.txt";
    private static final String SEPARATOR = "|";

    static class Assignment {
        private final int id;
        private final String subject;
        private final LocalDate deadline;
        private boolean completed;

        Assignment(int id, String subject, LocalDate deadline, boolean completed) {
            this.id = id;
            this.subject = subject;
            this.deadline = deadline;
            this.completed = completed;
        }

        String toFileLine() {
            return id + SEPARATOR + subject + SEPARATOR + deadline + SEPARATOR + completed;
        }

        static Assignment fromFileLine(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Expected 4 fields but found " + parts.length);
            }
            return new Assignment(
                    Integer.parseInt(parts[0].trim()),
                    parts[1].trim(),
                    LocalDate.parse(parts[2].trim()),
                    Boolean.parseBoolean(parts[3].trim()));
        }

        String describe() {
            if (completed) {
                return String.format("[%d] %-20s %s  (completed)", id, subject, deadline);
            }
            long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
            String when;
            if (days < 0) {
                when = "OVERDUE by " + (-days) + " day(s)";
            } else if (days == 0) {
                when = "due today";
            } else {
                when = "due in " + days + " day(s)";
            }
            return String.format("[%d] %-20s %s  (%s)", id, subject, deadline, when);
        }
    }

    private final List<Assignment> assignments = new ArrayList<>();
    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        new AssignmentTracker().run();
    }

    private String prompt(String message) {
        System.out.print(message);
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().trim();
    }

    private void run() {
        loadFromFile();
        System.out.println("=== Student Assignment Tracker ===");

        try (scanner) {
            while (true) {
                System.out.println();
                System.out.println("1. Add assignment");
                System.out.println("2. List pending assignments");
                System.out.println("3. Mark assignment complete");
                System.out.println("4. List all assignments");
                System.out.println("0. Exit");

                String choice = prompt("Choose an option: ");
                if (choice == null || choice.equals("0")) {
                    System.out.println("Goodbye! Your assignments are saved.");
                    break;
                }

                switch (choice) {
                    case "1" -> addAssignment();
                    case "2" -> listPending();
                    case "3" -> markComplete();
                    case "4" -> listAll();
                    default -> System.out.println("Please choose a number from the menu.");
                }
            }
        }
    }
    private void addAssignment() {
        String subject = prompt("Subject: ");
        if (subject == null) {
            return;
        }
        subject = subject.replace(SEPARATOR, "/");
        if (subject.isEmpty()) {
            System.out.println("Subject cannot be empty.");
            return;
        }

        LocalDate deadline = promptForDate();
        if (deadline == null) {
            return;
        }

        int newId = nextId();
        assignments.add(new Assignment(newId, subject, deadline, false));
        saveToFile();
        System.out.println("Added assignment [" + newId + "] " + subject + " due " + deadline + ".");
    }

    private LocalDate promptForDate() {
        String input = prompt("Deadline (yyyy-mm-dd): ");
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date. Use the format yyyy-mm-dd, for example 2026-10-20.");
            return null;
        }
    }

    private void listPending() {
        List<Assignment> pending = new ArrayList<>();
        for (Assignment a : assignments) {
            if (!a.completed) {
                pending.add(a);
            }
        }

        if (pending.isEmpty()) {
            System.out.println("No pending assignments. Nice work!");
            return;
        }

        pending.sort(Comparator.comparing(a -> a.deadline));
        System.out.println("Pending assignments (soonest first):");
        for (Assignment a : pending) {
            System.out.println("  " + a.describe());
        }
    }

    private void listAll() {
        if (assignments.isEmpty()) {
            System.out.println("No assignments recorded yet.");
            return;
        }

        List<Assignment> all = new ArrayList<>(assignments);
        all.sort(Comparator.comparing(a -> a.deadline));
        System.out.println("All assignments:");
        for (Assignment a : all) {
            System.out.println("  " + a.describe());
        }
    }

    private void markComplete() {
        String input = prompt("Enter the assignment ID to mark complete: ");
        if (input == null) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("That is not a valid ID.");
            return;
        }

        for (Assignment a : assignments) {
            if (a.id == id) {
                if (a.completed) {
                    System.out.println("Assignment [" + id + "] is already complete.");
                } else {
                    a.completed = true;
                    saveToFile();
                    System.out.println("Marked [" + id + "] " + a.subject + " as complete.");
                }
                return;
            }
        }
        System.out.println("No assignment found with ID " + id + ".");
    }

    private int nextId() {
        int max = 0;
        for (Assignment a : assignments) {
            max = Math.max(max, a.id);
        }
        return max + 1;
    }

    private void loadFromFile() {
        assignments.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    assignments.add(Assignment.fromFileLine(line));
                } catch (RuntimeException e) {
                    System.out.println("Skipping bad line " + lineNumber + " in " + DATA_FILE + ": " + e.getMessage());
                }
            }
            System.out.println("Loaded " + assignments.size() + " assignment(s) from " + DATA_FILE + ".");
        } catch (IOException e) {
            System.out.println("No saved assignments found yet. A new file will be created when you add one.");
        }
    }

    private void saveToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE, StandardCharsets.UTF_8))) {
            for (Assignment a : assignments) {
                writer.println(a.toFileLine());
            }
        } catch (IOException e) {
            System.out.println("Error saving assignments: " + e.getMessage());
        }
    }
}
