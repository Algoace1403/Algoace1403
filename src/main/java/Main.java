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
                
                List<String> tokens = parseArguments(input);
                if (tokens.isEmpty()) continue;
                
                String command = tokens.get(0);
                
                // 1. Check for exit
                if (command.equals("exit")) {
                    break;
                } 
                // 2. Check for echo
                else if (command.equals("echo")) {
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

    // --- UPGRADED PARSER METHOD ---
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false; // NEW: Track double quotes!
        boolean inToken = false; 

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Toggle single quote ONLY if we are NOT inside a double quote
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                inToken = true; 
            } 
            // Toggle double quote ONLY if we are NOT inside a single quote
            else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                inToken = true;
            } 
            // Break tokens on spaces ONLY if we are completely outside ALL quotes
            else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                if (inToken) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0); 
                    inToken = false;
                }
            } 
            // Otherwise, just add the letter
            else {
                currentToken.append(c);
                inToken = true;
            }
        }

        if (inToken) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}
