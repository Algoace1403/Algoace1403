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
                
                // --- NEW: Handle Output Redirection ---
                String redirectFile = null;
                int redirectIndex = -1;
                
                // Scan the tokens for > or 1>
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                        if (i + 1 < tokens.size()) {
                            redirectFile = tokens.get(i + 1);
                            redirectIndex = i;
                            break;
                        }
                    }
                }
                
                // If we found redirection, extract the pure command by removing the > and filename
                List<String> commandTokens = new ArrayList<>(tokens);
                if (redirectIndex != -1) {
                    commandTokens.subList(redirectIndex, redirectIndex + 2).clear();
                }
                
                if (commandTokens.isEmpty()) continue;
                String command = commandTokens.get(0);
                
                // 1. Check for exit
                if (command.equals("exit")) {
                    break;
                } 
                // 2. Check for echo
                else if (command.equals("echo")) {
                    String output = String.join(" ", commandTokens.subList(1, commandTokens.size()));
                    printOutput(output, redirectFile, currentDirectory);
                } 
                // 3. Check for pwd
                else if (command.equals("pwd")) {
                    printOutput(currentDirectory, redirectFile, currentDirectory);
                }
                // 4. Check for cd
                else if (command.equals("cd")) {
                    if (commandTokens.size() > 1) {
                        String targetDir = commandTokens.get(1);
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
                    if (commandTokens.size() > 1) {
                        String commandToCheck = commandTokens.get(1);
                        
                        if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || 
                            commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                            commandToCheck.equals("cd")) {
                            printOutput(commandToCheck + " is a shell builtin", redirectFile, currentDirectory);
                        } else {
                            String pathEnv = System.getenv("PATH");
                            boolean found = false;
                            if (pathEnv != null) {
                                String[] paths = pathEnv.split(":");
                                for (String path : paths) {
                                    File file = new File(path + "/" + commandToCheck);
                                    if (file.exists() && file.canExecute()) {
                                        printOutput(commandToCheck + " is " + file.getAbsolutePath(), redirectFile, currentDirectory);
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            if (!found) {
                                printOutput(commandToCheck + ": not found", redirectFile, currentDirectory);
                            }
                        }
                    }
                }
                // 6. Run external program
                else {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(commandTokens);
                        pb.directory(new File(currentDirectory));
                        
                        // NEW: Route external program output to the file if requested!
                        if (redirectFile != null) {
                            File rFile = Paths.get(currentDirectory).resolve(redirectFile).toFile();
                            pb.redirectOutput(rFile);
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT); // Keep errors on screen
                        } else {
                            pb.inheritIO(); 
                        }
                        
                        Process process = pb.start();
                        process.waitFor(); 
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }

    // --- NEW HELPER METHOD FOR BUILT-INS ---
    private static void printOutput(String output, String redirectFile, String currentDirectory) throws Exception {
        if (redirectFile != null) {
            Path filePath = Paths.get(currentDirectory).resolve(redirectFile);
            // writeString handles creating the file or overwriting it automatically
            Files.writeString(filePath, output + "\n");
        } else {
            System.out.println(output);
        }
    }

    // --- BULLETPROOF PARSER METHOD ---
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false; 
        boolean inToken = false; 

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\') {
                if (inSingleQuote) {
                    currentToken.append(c);
                    inToken = true;
                } else if (inDoubleQuote) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"' || next == '$' || next == '\n') {
                            currentToken.append(next);
                            i++; 
                        } else {
                            currentToken.append(c); 
                        }
                        inToken = true;
                    }
                } else {
                    if (i + 1 < input.length()) {
                        currentToken.append(input.charAt(i + 1));
                        i++; 
                        inToken = true;
                    }
                }
            } 
            else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                inToken = true; 
            } 
            else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                inToken = true;
            } 
            else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                if (inToken) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0); 
                    inToken = false;
                }
            } 
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