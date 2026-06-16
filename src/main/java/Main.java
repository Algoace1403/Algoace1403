import java.util.Scanner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    static class Job {
        int id;
        Process process;
        String command;

        public Job(int id, Process process, String command) {
            this.id = id;
            this.process = process;
            this.command = command;
        }
    }

    private static List<Job> backgroundJobs = new ArrayList<>();
    private static int nextJobId = 1;

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
                
                boolean isBackground = false;
                if (tokens.get(tokens.size() - 1).equals("&")) {
                    isBackground = true;
                    tokens.remove(tokens.size() - 1); 
                    if (tokens.isEmpty()) continue; 
                }

                List<String> commandTokens = new ArrayList<>();
                String redirectOutFile = null;
                String redirectErrFile = null;
                boolean appendOut = false;
                boolean appendErr = false; 
                
                for (int i = 0; i < tokens.size(); i++) {
                    String t = tokens.get(i);
                    if (t.equals(">>") || t.equals("1>>")) {
                        if (i + 1 < tokens.size()) {
                            redirectOutFile = tokens.get(i + 1);
                            appendOut = true;
                            i++; 
                        }
                    } else if (t.equals(">") || t.equals("1>")) {
                        if (i + 1 < tokens.size()) {
                            redirectOutFile = tokens.get(i + 1);
                            appendOut = false;
                            i++; 
                        }
                    } else if (t.equals("2>>")) {
                        if (i + 1 < tokens.size()) {
                            redirectErrFile = tokens.get(i + 1);
                            appendErr = true;
                            i++; 
                        }
                    } else if (t.equals("2>")) {
                        if (i + 1 < tokens.size()) {
                            redirectErrFile = tokens.get(i + 1);
                            appendErr = false;
                            i++; 
                        }
                    } else {
                        commandTokens.add(t);
                    }
                }
                
                if (commandTokens.isEmpty()) continue;
                String command = commandTokens.get(0);
                
                if (redirectOutFile != null && !appendOut) {
                    File rFile = Paths.get(currentDirectory).resolve(redirectOutFile).toFile();
                    if (rFile.getParentFile() != null) rFile.getParentFile().mkdirs();
                    Files.writeString(rFile.toPath(), ""); 
                }
                if (redirectErrFile != null && !appendErr) {
                    File eFile = Paths.get(currentDirectory).resolve(redirectErrFile).toFile();
                    if (eFile.getParentFile() != null) eFile.getParentFile().mkdirs();
                    Files.writeString(eFile.toPath(), ""); 
                }

                // 1. Check for exit
                if (command.equals("exit")) {
                    break;
                } 
                // 2. Check for echo
                else if (command.equals("echo")) {
                    String output = String.join(" ", commandTokens.subList(1, commandTokens.size()));
                    printOutput(output, redirectOutFile, currentDirectory, appendOut);
                } 
                // 3. Check for pwd
                else if (command.equals("pwd")) {
                    printOutput(currentDirectory, redirectOutFile, currentDirectory, appendOut);
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
                            printError("cd: " + targetDir + ": No such file or directory", redirectErrFile, currentDirectory, appendErr);
                        }
                    }
                }
                // 5. Check for jobs (UPGRADED!)
                else if (command.equals("jobs")) {
                    List<String> outputLines = new ArrayList<>();
                    for (Job job : backgroundJobs) {
                        // Ask the OS if the background thread is still alive
                        String status = job.process.isAlive() ? "Running" : "Done";
                        // Reconstruct the standard Unix formatting
                        outputLines.add("[" + job.id + "] + " + status + " " + job.command + " &");
                    }
                    
                    if (!outputLines.isEmpty()) {
                        printOutput(String.join("\n", outputLines), redirectOutFile, currentDirectory, appendOut);
                    }
                }
                // 6. Check for type
                else if (command.equals("type")) {
                    if (commandTokens.size() > 1) {
                        String commandToCheck = commandTokens.get(1);
                        
                        if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || 
                            commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                            commandToCheck.equals("cd") || commandToCheck.equals("jobs")) {
                            printOutput(commandToCheck + " is a shell builtin", redirectOutFile, currentDirectory, appendOut);
                        } else {
                            String pathEnv = System.getenv("PATH");
                            boolean found = false;
                            if (pathEnv != null) {
                                String[] paths = pathEnv.split(":");
                                for (String path : paths) {
                                    File file = new File(path + "/" + commandToCheck);
                                    if (file.exists() && file.canExecute()) {
                                        printOutput(commandToCheck + " is " + file.getAbsolutePath(), redirectOutFile, currentDirectory, appendOut);
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            if (!found) {
                                printError(commandToCheck + ": not found", redirectErrFile, currentDirectory, appendErr);
                            }
                        }
                    }
                }
                // 7. Run external program
                else {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(commandTokens);
                        pb.directory(new File(currentDirectory));
                        
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        
                        if (redirectOutFile != null) {
                            File rFile = Paths.get(currentDirectory).resolve(redirectOutFile).toFile();
                            if (rFile.getParentFile() != null) rFile.getParentFile().mkdirs();
                            if (appendOut) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(rFile));
                            } else {
                                pb.redirectOutput(rFile);
                            }
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT); 
                        }

                        if (redirectErrFile != null) {
                            File eFile = Paths.get(currentDirectory).resolve(redirectErrFile).toFile();
                            if (eFile.getParentFile() != null) eFile.getParentFile().mkdirs();
                            
                            if (appendErr) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(eFile));
                            } else {
                                pb.redirectError(eFile);
                            }
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT); 
                        }
                        
                        Process process = pb.start();
                        
                        if (isBackground) {
                            Job job = new Job(nextJobId++, process, String.join(" ", commandTokens));
                            backgroundJobs.add(job);
                            System.out.println("[" + job.id + "] " + process.pid());
                        } else {
                            process.waitFor(); 
                        }
                        
                    } catch (Exception e) {
                        printError(command + ": command not found", redirectErrFile, currentDirectory, appendErr);
                    }
                }
            }
        }
    }

    private static void printOutput(String output, String redirectFile, String currentDirectory, boolean appendOut) throws Exception {
        if (redirectFile != null) {
            Path filePath = Paths.get(currentDirectory).resolve(redirectFile);
            File file = filePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            
            if (appendOut) {
                Files.writeString(filePath, output + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(filePath, output + "\n");
            }
        } else {
            System.out.println(output);
        }
    }

    private static void printError(String errorMsg, String redirectFile, String currentDirectory, boolean appendErr) throws Exception {
        if (redirectFile != null) {
            Path filePath = Paths.get(currentDirectory).resolve(redirectFile);
            File file = filePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            
            if (appendErr) {
                Files.writeString(filePath, errorMsg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(filePath, errorMsg + "\n");
            }
        } else {
            System.out.println(errorMsg);
        }
    }

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