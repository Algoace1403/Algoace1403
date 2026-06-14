import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
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
                    String commandToCheck = input.substring(5);
                    
                    if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || commandToCheck.equals("type")) {
                        System.out.println(commandToCheck + " is a shell builtin");
                    } else {
                        // Search the PATH for the executable
                        String pathEnv = System.getenv("PATH");
                        boolean found = false;
                        
                        if (pathEnv != null) {
                            // Split the PATH by ":" (which is the separator used on macOS)
                            String[] paths = pathEnv.split(":");
                            for (String path : paths) {
                                File file = new File(path + "/" + commandToCheck);
                                if (file.exists() && file.canExecute()) {
                                    System.out.println(commandToCheck + " is " + file.getAbsolutePath());
                                    found = true;
                                    break; // Stop searching once found
                                }
                            }
                        }
                        
                        if (!found) {
                            System.out.println(commandToCheck + ": not found");
                        }
                    }
                }
                // 4. Invalid command
                else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }
}