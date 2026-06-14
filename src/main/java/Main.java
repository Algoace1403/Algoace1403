import java.util.Scanner;

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
                    
                    // Check if it's a known builtin
                    if (commandToCheck.equals("exit") || commandToCheck.equals("echo") || commandToCheck.equals("type")) {
                        System.out.println(commandToCheck + " is a shell builtin");
                    } else {
                        System.out.println(commandToCheck + ": not found");
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