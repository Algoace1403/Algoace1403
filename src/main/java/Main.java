import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        // The "Loop" part of the REPL
        while (true) {
            // 1. Print the prompt
            System.out.print("$ ");
            
            // 2. Read the input
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                
                // 3. Evaluate and Print (For now, everything is invalid)
                System.out.println(input + ": command not found");
            }
        }
    }
}