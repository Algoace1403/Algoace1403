import java.util.Scanner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");
        
        while (true) {
            System.out.print("$ ");
            
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
                if (input.trim().isEmpty()) {
                    continue;
                }
                
                // NEW: Use our custom parser to break the input into safe pieces
                List<String> tokens = parseArguments(input);
                if (tokens.isEmpty()) continue;
                
                String command = tokens.get(0); // The first piece is always the command
                
                // 1. Check for exit
                if (command.equals("exit")) {
                    break;
                } 
                // 2. Check for echo
                else if (command.equals("echo")) {
                    // Rejoin the remaining pieces with a single space and print
                    String output = String.join(" ", tokens.subList(1, tokens.size()));
                    System.out.println(output);
                } 
                // 3. Check for pwd
                else if (command.equals("pwd")) {
                    System.out.println(currentDirectory);
                }
                // 4. Check for cd
                else if (command.equals("cd")) {
                    if (tokens.size() > 1) {
                        String targetDir = tokens.get(1);
                        
                        if (targetDir.startsWith("~")) {
                            String homeDir = System.getenv("HOME");
                            if (homeDir != null) {
                                targetDir = targetDir.replaceFirst("^~", homeDir);
                            }
                        }
                        
                        Path currentPath = Paths.get(currentDirectory);
                        Path resolvedPath = currentPath.resolve(targetDir).normalize();
                        
                        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                            currentDirectory = resolvedPath.toAbsolutePath().toString();
                        } else {
                            System.out.println("cd: " + targetDir + ": No such file or directory");
                        }
                    }
                }
                // 5. Check for type
                else if (command.equals("type")) {
                    if (tokens.size() > 1) {
                        String commandToCheck = tokens.get(1);
                        
                        if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || 
                            commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                            commandToCheck.equals("cd")) {
                            System.out.println(commandToCheck + " is a shell builtin");
                        } else {
                            String pathEnv = System.getenv("PATH");
                            boolean found = false;
                            
                            if (pathEnv != null) {
                                String[] paths = pathEnv.split(":");
                                for (String path : paths) {
                                    File file = new File(path + "/" + commandToCheck);
                                    if (file.exists() && file.canExecute()) {
                                        System.out.println(commandToCheck + " is " + file.getAbsolutePath());
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (!found) {
                                System.out.println(commandToCheck + ": not found");
                            }
                        }
                    }
                }
                // 6. Run external program
                else {
                    try {
                        // ProcessBuilder accepts our List<String> directly!
                        ProcessBuilder pb = new ProcessBuilder(tokens);
                        pb.directory(new File(currentDirectory));
                        pb.inheritIO(); 
                        Process process = pb.start();
                        process.waitFor(); 
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }

    // --- NEW PARSER METHOD ---
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inToken = false; 

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                // If we hit a quote, toggle our state but do NOT add the quote to the token
                inSingleQuote = !inSingleQuote;
                inToken = true; 
            } else if (c == ' ' && !inSingleQuote) {
                // If we see a space OUTSIDE of quotes, the current token is finished
                if (inToken) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0); // Clear the builder for the next word
                    inToken = false;
                }
            } else {
                // Otherwise, just add the letter to our current token
                currentToken.append(c);
                inToken = true;
            }
        }

        // Add the very last token if we finished the loop while building one
        if (inToken) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}