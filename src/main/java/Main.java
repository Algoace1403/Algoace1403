import java.util.Scanner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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
    private static LinkedList<Integer> jobHistory = new LinkedList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");
        
        while (true) {
            
            // --- REAP BEFORE PROMPT ---
            List<Job> reaped = new ArrayList<>();
            List<Job> sortedReap = new ArrayList<>(backgroundJobs);
            sortedReap.sort((j1, j2) -> Integer.compare(j1.id, j2.id));

            for (Job job : sortedReap) {
                try {
                    job.process.waitFor(25, TimeUnit.MILLISECONDS);
                } catch (Exception e) {}

                if (!job.process.isAlive()) {
                    char marker = ' ';
                    if (!jobHistory.isEmpty() && jobHistory.getLast() == job.id) {
                        marker = '+';
                    } else if (jobHistory.size() >= 2 && jobHistory.get(jobHistory.size() - 2) == job.id) {
                        marker = '-';
                    }
                    
                    System.out.printf("[%d]%c  Done                    %s\n", job.id, marker, job.command);
                    reaped.add(job);
                }
            }
            
            for (Job j : reaped) {
                backgroundJobs.remove(j);
                jobHistory.remove((Integer) j.id);
            }

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

                // --- MULTI-COMMAND PIPELINE LOGIC ---
                int pipeIdx = commandTokens.indexOf("|");
                if (pipeIdx != -1) {
                    List<List<String>> pipelineCommands = new ArrayList<>();
                    List<String> currentCmd = new ArrayList<>();
                    for (String t : commandTokens) {
                        if (t.equals("|")) {
                            pipelineCommands.add(currentCmd);
                            currentCmd = new ArrayList<>();
                        } else {
                            currentCmd.add(t);
                        }
                    }
                    pipelineCommands.add(currentCmd);

                    String builtinInput = null;
                    int startIndex = 0;
                    
                    // 1. Check if first command is builtin
                    if (isBuiltin(pipelineCommands.get(0).get(0))) {
                        builtinInput = executeBuiltinToString(pipelineCommands.get(0), currentDirectory, null, false);
                        startIndex = 1;
                    }
                    
                    // 2. Check if last command is builtin
                    int endIndex = pipelineCommands.size();
                    boolean lastIsBuiltin = false;
                    if (startIndex < pipelineCommands.size() && isBuiltin(pipelineCommands.get(pipelineCommands.size() - 1).get(0))) {
                        lastIsBuiltin = true;
                        endIndex = pipelineCommands.size() - 1;
                    }
                    
                    // 3. Build external processes for the middle
                    List<ProcessBuilder> builders = new ArrayList<>();
                    for (int i = startIndex; i < endIndex; i++) {
                        ProcessBuilder pb = new ProcessBuilder(pipelineCommands.get(i));
                        pb.directory(new File(currentDirectory));
                        builders.add(pb);
                    }

                    try {
                        if (!builders.isEmpty()) {
                            // Only apply redirects to the final external output
                            if (!lastIsBuiltin) {
                                ProcessBuilder lastPb = builders.get(builders.size() - 1);
                                applyProcessRedirects(lastPb, redirectOutFile, redirectErrFile, appendOut, appendErr, currentDirectory);
                            } else {
                                ProcessBuilder lastPb = builders.get(builders.size() - 1);
                                lastPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                            }
                            
                            List<Process> processes = ProcessBuilder.startPipeline(builders);
                            
                            // Feed the builtin string output to the first external process
                            if (builtinInput != null) {
                                java.io.OutputStream os = processes.get(0).getOutputStream();
                                os.write(builtinInput.getBytes());
                                os.flush();
                                os.close();
                            }
                            
                            Process lastExtProcess = processes.get(processes.size() - 1);
                            
                            // If the pipeline ended in a built-in, execute it now that external jobs are running
                            if (lastIsBuiltin) {
                                lastExtProcess.waitFor();
                                String rightOut = executeBuiltinToString(pipelineCommands.get(pipelineCommands.size() - 1), currentDirectory, redirectErrFile, appendErr);
                                if (rightOut.endsWith("\n")) rightOut = rightOut.substring(0, rightOut.length() - 1);
                                if (!rightOut.isEmpty()) printOutput(rightOut, redirectOutFile, currentDirectory, appendOut);
                            } else {
                                handleBackgroundJob(lastExtProcess, isBackground, commandTokens);
                            }
                        } else {
                            // Edge case: Pipeline is entirely built-ins
                            if (lastIsBuiltin) {
                                String rightOut = executeBuiltinToString(pipelineCommands.get(pipelineCommands.size() - 1), currentDirectory, redirectErrFile, appendErr);
                                if (rightOut.endsWith("\n")) rightOut = rightOut.substring(0, rightOut.length() - 1);
                                if (!rightOut.isEmpty()) printOutput(rightOut, redirectOutFile, currentDirectory, appendOut);
                            }
                        }
                    } catch (Exception e) {
                        printError("pipeline command not found", redirectErrFile, currentDirectory, appendErr);
                    }
                    continue; 
                }

                // --- SINGLE COMMAND LOGIC ---
                if (command.equals("exit")) {
                    break;
                } 
                else if (command.equals("echo")) {
                    String output = String.join(" ", commandTokens.subList(1, commandTokens.size()));
                    printOutput(output, redirectOutFile, currentDirectory, appendOut);
                } 
                else if (command.equals("pwd")) {
                    printOutput(currentDirectory, redirectOutFile, currentDirectory, appendOut);
                }
                else if (command.equals("cd")) {
                    if (commandTokens.size() > 1) {
                        String targetDir = commandTokens.get(1);
                        if (targetDir.startsWith("~")) {
                            String homeDir = System.getenv("HOME");
                            if (homeDir != null) targetDir = targetDir.replaceFirst("^~", homeDir);
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
                else if (command.equals("jobs")) {
                    String rightOut = executeBuiltinToString(commandTokens, currentDirectory, redirectErrFile, appendErr);
                    if (rightOut.endsWith("\n")) rightOut = rightOut.substring(0, rightOut.length() - 1);
                    if (!rightOut.isEmpty()) printOutput(rightOut, redirectOutFile, currentDirectory, appendOut);
                }
                else if (command.equals("type")) {
                    String rightOut = executeBuiltinToString(commandTokens, currentDirectory, redirectErrFile, appendErr);
                    if (rightOut.endsWith("\n")) rightOut = rightOut.substring(0, rightOut.length() - 1);
                    if (!rightOut.isEmpty()) printOutput(rightOut, redirectOutFile, currentDirectory, appendOut);
                }
                else {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(commandTokens);
                        pb.directory(new File(currentDirectory));
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        applyProcessRedirects(pb, redirectOutFile, redirectErrFile, appendOut, appendErr, currentDirectory);
                        
                        Process process = pb.start();
                        handleBackgroundJob(process, isBackground, commandTokens);
                        
                    } catch (Exception e) {
                        printError(command + ": command not found", redirectErrFile, currentDirectory, appendErr);
                    }
                }
            }
        }
    }

    // --- HELPER METHODS ---

    private static boolean isBuiltin(String command) {
        return command.equals("echo") || command.equals("pwd") || 
               command.equals("type") || command.equals("jobs") || 
               command.equals("cd") || command.equals("exit");
    }

    private static String executeBuiltinToString(List<String> cmdTokens, String currentDirectory, String redirectErrFile, boolean appendErr) throws Exception {
        String lCommand = cmdTokens.get(0);
        StringBuilder output = new StringBuilder();
        
        if (lCommand.equals("echo")) {
            output.append(String.join(" ", cmdTokens.subList(1, cmdTokens.size()))).append("\n");
        } else if (lCommand.equals("pwd")) {
            output.append(currentDirectory).append("\n");
        } else if (lCommand.equals("type")) {
            if (cmdTokens.size() > 1) {
                String cmdToCheck = cmdTokens.get(1);
                if (isBuiltin(cmdToCheck)) {
                    output.append(cmdToCheck).append(" is a shell builtin\n");
                } else {
                    String pathEnv = System.getenv("PATH");
                    boolean found = false;
                    if (pathEnv != null) {
                        for (String p : pathEnv.split(":")) {
                            File f = new File(p + "/" + cmdToCheck);
                            if (f.exists() && f.canExecute()) {
                                output.append(cmdToCheck).append(" is ").append(f.getAbsolutePath()).append("\n");
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        printError(cmdToCheck + ": not found", redirectErrFile, currentDirectory, appendErr);
                    }
                }
            }
        } else if (lCommand.equals("jobs")) {
            List<Job> sortedJobs = new ArrayList<>(backgroundJobs);
            sortedJobs.sort((j1, j2) -> Integer.compare(j1.id, j2.id));
            List<Job> toRemove = new ArrayList<>();
            
            for (Job job : sortedJobs) {
                try { job.process.waitFor(10, TimeUnit.MILLISECONDS); } catch(Exception e){}
                char marker = ' ';
                if (!jobHistory.isEmpty() && jobHistory.getLast() == job.id) marker = '+';
                else if (jobHistory.size() >= 2 && jobHistory.get(jobHistory.size() - 2) == job.id) marker = '-';
                
                if (job.process.isAlive()) {
                    output.append(String.format("[%d]%c  Running                 %s &\n", job.id, marker, job.command));
                } else {
                    output.append(String.format("[%d]%c  Done                    %s\n", job.id, marker, job.command));
                    toRemove.add(job);
                }
            }
            
            for (Job j : toRemove) {
                backgroundJobs.remove(j);
                jobHistory.remove((Integer) j.id);
            }
        }
        return output.toString();
    }

    private static void handleBackgroundJob(Process process, boolean isBackground, List<String> commandTokens) throws Exception {
        if (isBackground && process != null) {
            int newJobId = 1;
            while (true) {
                boolean taken = false;
                for (Job j : backgroundJobs) {
                    if (j.id == newJobId) { taken = true; break; }
                }
                if (!taken) break;
                newJobId++;
            }
            
            Job job = new Job(newJobId, process, String.join(" ", commandTokens));
            backgroundJobs.add(job);
            
            jobHistory.remove((Integer) newJobId); 
            jobHistory.addLast(newJobId);
            
            System.out.println("[" + job.id + "] " + process.pid());
        } else if (process != null) {
            process.waitFor(); 
        }
    }

    private static void applyProcessRedirects(ProcessBuilder pb, String redirectOutFile, String redirectErrFile, boolean appendOut, boolean appendErr, String currentDirectory) {
        if (redirectOutFile != null) {
            File rFile = Paths.get(currentDirectory).resolve(redirectOutFile).toFile();
            if (appendOut) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(rFile));
            else pb.redirectOutput(rFile);
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }

        if (redirectErrFile != null) {
            File eFile = Paths.get(currentDirectory).resolve(redirectErrFile).toFile();
            if (appendErr) pb.redirectError(ProcessBuilder.Redirect.appendTo(eFile));
            else pb.redirectError(eFile);
        } else {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
    }

    private static void printOutput(String output, String redirectFile, String currentDirectory, boolean appendOut) throws Exception {
        if (redirectFile != null) {
            Path filePath = Paths.get(currentDirectory).resolve(redirectFile);
            File file = filePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            
            if (appendOut) Files.writeString(filePath, output + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else Files.writeString(filePath, output + "\n");
        } else {
            System.out.println(output);
        }
    }

    private static void printError(String errorMsg, String redirectFile, String currentDirectory, boolean appendErr) throws Exception {
        if (redirectFile != null) {
            Path filePath = Paths.get(currentDirectory).resolve(redirectFile);
            File file = filePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            
            if (appendErr) Files.writeString(filePath, errorMsg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else Files.writeString(filePath, errorMsg + "\n");
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