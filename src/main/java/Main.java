import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        // Track the current working directory for Navigation stages
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
                // 3. Check for pwd (NEW!)
                else if (input.equals("pwd")) {
                    System.out.println(currentDirectory);
                }
                // 4. Check for type
                else if (input.startsWith("type ")) {
                    String commandToCheck = input.substring(5).trim();
                    
                    // Added pwd to our list of known builtins!
                    if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || commandToCheck.equals("type") || commandToCheck.equals("pwd")) {
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
                // 5. Run external program
                else {
                    String[] cmdArgs = input.split(" ");
                    String command = cmdArgs[0];
                    
                    try {
                        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
                        // Tell the external program to run inside our current directory
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