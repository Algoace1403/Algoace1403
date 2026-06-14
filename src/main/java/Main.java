import java.util.Scanner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

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
                
                // 1. Check for exit
                if (input.equals("exit 0") || input.equals("exit")) {
                    break;
                } 
                // 2. Check for echo
                else if (input.startsWith("echo ")) {
                    System.out.println(input.substring(5));
                } 
                // 3. Check for pwd
                else if (input.equals("pwd")) {
                    System.out.println(currentDirectory);
                }
                // 4. Check for cd (UPGRADED for Home Directory!)
                else if (input.startsWith("cd ")) {
                    String targetDir = input.substring(3).trim();
                    
                    // NEW: Handle the '~' symbol
                    if (targetDir.startsWith("~")) {
                        String homeDir = System.getenv("HOME");
                        if (homeDir != null) {
                            // Replace the leading '~' with the actual home directory path
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
                // 5. Check for type
                else if (input.startsWith("type ")) {
                    String commandToCheck = input.substring(5).trim();
                    
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
                // 6. Run external program
                else {
                    String[] cmdArgs = input.split(" ");
                    String command = cmdArgs[0];
                    
                    try {
                        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
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
}