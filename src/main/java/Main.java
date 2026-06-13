import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. Print the prompt. 
        // We use print() instead of println() so the cursor stays on the same line.
        System.out.print("$ ");
        
        // 2. Wait for user input so the shell doesn't immediately exit.
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String input = scanner.nextLine();
        }
    }
}