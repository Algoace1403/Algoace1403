import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
                // Handle empty input (just hitting Enter)
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
                // 3. Check for type
                else if (input.startsWith("type ")) {
                    String commandToCheck = input.substring(5).trim();
                    
                    if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || commandToCheck.equals("type")) {
                        System.out.println(commandToCheck + " is a shell builtin");
                    } else {
                        // Search the PATH for the executable
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
                // 4. Run external program!
                else {
                    // Split the input into the command and its arguments (e.g., "ls" "-l" "/tmp")
                    String[] cmdArgs = input.split(" ");
                    String command = cmdArgs[0];
                    
                    try {
                        // ProcessBuilder asks the Mac OS to run this array of strings
                        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
                        
                        // inheritIO() is magic: it connects the new program's output directly to your terminal screen
                        pb.inheritIO(); 
                        
                        Process process = pb.start();
                        
                        // Wait for the external program to finish before printing the next "$ "
                        process.waitFor(); 
                    } catch (Exception e) {
                        // If the OS throws an error because the file doesn't exist in the PATH
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }
}