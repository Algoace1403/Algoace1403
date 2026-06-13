import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
                // 1. Check for the exit command
                if (input.equals("exit 0") || input.equals("exit")) {
                    break;
                } 
                // 2. Check for the echo command
                else if (input.startsWith("echo ")) {
                    // Extract and print everything after "echo " (index 5)
                    System.out.println(input.substring(5));
                } 
                // 3. Otherwise, treat it as an invalid command
                else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }
}