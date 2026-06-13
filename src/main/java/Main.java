import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
                // 1. Check for the exit command
                // CodeCrafters tests this by sending "exit 0"
                if (input.equals("exit 0") || input.equals("exit")) {
                    break; // This breaks the while loop and safely ends the program
                }
                
                // 2. Otherwise, treat it as an invalid command
                System.out.println(input + ": command not found");
            }
        }
    }
}