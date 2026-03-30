// ============================================================
// DEPRECATED — This was the v0.1 starter code (single Claude call).
// Kept for reference only. DO NOT RUN OR MODIFY THIS FILE.
// The active code is in src/main/java/collab/.
// ============================================================
//
// MainV01_Starter.java — Your first AI API call (archived)
// ============================================================
//
// WHAT THIS FILE DOES:
// 1. Asks you to type a question
// 2. Sends that question to Claude's API over the internet
// 3. Prints Claude's response in your terminal
//
// That's it. No frameworks, no libraries, no magic.
// Just Java talking to an API.
//
// HOW TO RUN:
//   javac Main.java
//   java Main
//
// PREREQUISITES:
//   - Java 21 installed (you have this)
//   - An Anthropic API key (get one at console.anthropic.com)
// ============================================================


// IMPORTS — these are Java's built-in tools. Nothing external.
// Think of each import as borrowing a specific tool from Java's toolbox.

import java.net.URI;                    // Represents a web address (URL)
import java.net.http.HttpClient;        // The thing that sends HTTP requests (like a browser, but in code)
import java.net.http.HttpRequest;       // A single request we're building to send
import java.net.http.HttpResponse;      // The response that comes back
import java.util.Scanner;              // Reads keyboard input from the terminal


public class Main {

    // ============================================================
    // CONSTANTS — values that never change while the program runs.
    //
    // WHY CONSTANTS?
    // If you hardcode "https://api.anthropic.com/v1/messages"
    // in five different places and the URL changes, you have to
    // find and fix all five. With a constant, you fix it once.
    // ============================================================

    // The web address where Claude's API lives.
    // Every AI company has one of these. It's like a phone number for their AI.
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    // Your personal key that proves you're allowed to use the API.
    // IMPORTANT: Never commit this to Git. Never share it publicly.
    // We'll improve how this is stored later. For now, paste yours here.
    private static final String API_KEY = "YOUR_KEY_HERE";

    // Which version of Claude to use. "claude-sonnet-4-20250514" is fast and affordable.
    // Other options: "claude-opus-4-0-20250514" (smarter, slower, costs more)
    private static final String MODEL = "claude-sonnet-4-20250514";


    // ============================================================
    // main() — where Java starts running your program.
    //
    // Every Java program needs exactly one main method.
    // 'public'  = other code can call this
    // 'static'  = runs without creating an object first
    // 'void'    = doesn't return a value
    // 'String[] args' = command-line arguments (we won't use these yet)
    // ============================================================

    public static void main(String[] args) {

        // Scanner reads text from the keyboard.
        // System.in = "the terminal input stream"
        Scanner scanner = new Scanner(System.in);

        // Greet the user and ask for input
        System.out.println("========================================");
        System.out.println("  AI Collaboration Platform v0.1");
        System.out.println("  Type your prompt, then press Enter.");
        System.out.println("========================================");
        System.out.println();
        System.out.print("You: ");

        // scanner.nextLine() pauses the program and waits for
        // the user to type something and press Enter.
        // Whatever they typed gets stored in the 'userPrompt' variable.
        String userPrompt = scanner.nextLine();

        // Now we call Claude's API with that prompt.
        // This is the core of the entire program.
        System.out.println();
        System.out.println("Sending to Claude...");
        System.out.println();

        // callClaude() is a method WE wrote (see below).
        // It takes the user's text, sends it to the API,
        // and returns Claude's response as a String.
        String response = callClaude(userPrompt);

        // Print the result
        System.out.println("Claude: " + response);

        // Clean up: close the scanner to release the keyboard resource.
        // Not strictly necessary for a simple program, but it's good practice.
        scanner.close();
    }


    // ============================================================
    // callClaude() — sends a prompt to Claude and returns the response.
    //
    // This is where the actual internet communication happens.
    // It follows a simple pattern that ALL APIs use:
    //
    //   1. BUILD the request (what to send, where to send it)
    //   2. SEND the request over the internet
    //   3. READ the response that comes back
    //
    // 'private' = only this file can use this method
    // 'static'  = doesn't need an object instance
    // 'String'  = this method returns text (Claude's answer)
    // ============================================================

    private static String callClaude(String userPrompt) {

        // --------------------------------------------------------
        // STEP 1: BUILD THE JSON BODY
        // --------------------------------------------------------
        //
        // APIs communicate using JSON (JavaScript Object Notation).
        // JSON looks like this:
        //   { "key": "value", "number": 42, "list": ["a", "b"] }
        //
        // Claude's API expects a specific JSON structure:
        //   {
        //     "model": "which Claude model to use",
        //     "max_tokens": how many words max in the response,
        //     "messages": [
        //       { "role": "user", "content": "your question here" }
        //     ]
        //   }
        //
        // We're building this JSON as a plain String.
        // Later, we'll use a library (Gson) to do this more safely.
        // For now, this teaches you what JSON actually looks like.
        // --------------------------------------------------------

        // We need to "escape" any double quotes or backslashes in the
        // user's input, because those characters have special meaning
        // inside a JSON string. Without escaping, a prompt like:
        //   He said "hello"
        // would break the JSON structure.
        String escapedPrompt = userPrompt
                .replace("\\", "\\\\")   // escape backslashes first
                .replace("\"", "\\\"")   // then escape double quotes
                .replace("\n", "\\n")    // then escape newlines
                .replace("\r", "\\r")    // then escape carriage returns
                .replace("\t", "\\t");   // then escape tabs

        // Now build the JSON request body.
        // The """ syntax is a Java "text block" (available since Java 15).
        // It lets you write multi-line strings without ugly concatenation.
        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 1024,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(MODEL, escapedPrompt);
        // .formatted() replaces each %s with the corresponding variable.
        // First %s becomes MODEL, second %s becomes escapedPrompt.


        // --------------------------------------------------------
        // STEP 2: BUILD AND SEND THE HTTP REQUEST
        // --------------------------------------------------------
        //
        // An HTTP request has three parts:
        //   - URL:     where to send it
        //   - HEADERS: metadata (your API key, content type)
        //   - BODY:    the actual data (our JSON)
        //
        // Think of it like mailing a letter:
        //   - URL     = the mailing address
        //   - HEADERS = the return address label and "FRAGILE" sticker
        //   - BODY    = the letter inside the envelope
        // --------------------------------------------------------

        try {
            // HttpClient is Java's built-in web browser (without the GUI).
            // .newHttpClient() creates a fresh one with default settings.
            HttpClient client = HttpClient.newHttpClient();

            // Build the request piece by piece using the "builder" pattern.
            // Each method adds one detail, and .build() finalizes it.
            HttpRequest request = HttpRequest.newBuilder()

                    // WHERE to send it
                    .uri(URI.create(API_URL))

                    // METADATA: tell the API who we are and what we're sending
                    .header("Content-Type", "application/json")      // "I'm sending JSON"
                    .header("x-api-key", API_KEY)                    // "Here's my key"
                    .header("anthropic-version", "2023-06-01")       // "Use this API version"

                    // WHAT to send (our JSON body), using HTTP POST method.
                    // POST = "I'm sending data for you to process"
                    // (vs GET = "just give me information")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))

                    // Finalize the request object
                    .build();

            // SEND IT.
            // .send() transmits the request over the internet and waits
            // for the response. This is a "blocking" call — the program
            // pauses here until Claude's server responds (usually 2-10 seconds).
            //
            // BodyHandlers.ofString() means "give me the response as text"
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());


            // --------------------------------------------------------
            // STEP 3: READ THE RESPONSE
            // --------------------------------------------------------
            //
            // The response comes back as JSON too. It looks like:
            //   {
            //     "content": [
            //       {
            //         "type": "text",
            //         "text": "Claude's actual answer here"
            //       }
            //     ],
            //     ... other fields we don't need yet ...
            //   }
            //
            // We need to extract just the "text" value from that structure.
            // --------------------------------------------------------

            // response.statusCode() tells us if the request succeeded.
            // 200 = success. Anything else = something went wrong.
            if (response.statusCode() != 200) {
                return "ERROR: API returned status " + response.statusCode()
                        + "\nResponse: " + response.body();
            }

            // Extract Claude's answer from the JSON response.
            // This is a simple string search — not elegant, but it works
            // and you can SEE exactly what's happening.
            // We'll replace this with proper JSON parsing (Gson) later.
            String body = response.body();

            return extractTextFromResponse(body);

        } catch (Exception e) {
            // If ANYTHING goes wrong (network error, bad URL, timeout),
            // Java throws an "exception." catch blocks handle those errors
            // gracefully instead of crashing the program.
            //
            // e.getMessage() gives us a human-readable error description.
            return "ERROR: " + e.getMessage();
        }
    }


    // ============================================================
    // extractTextFromResponse() — pulls Claude's answer out of the JSON.
    //
    // WHY THIS IS UGLY (and why that's fine for now):
    // We're doing manual string searching because we haven't added
    // a JSON library yet. This teaches you what JSON parsing actually
    // does under the hood. When we add Gson in Week 2, you'll
    // appreciate why libraries exist.
    //
    // WHAT WE'RE LOOKING FOR:
    // The response JSON contains:  "text": "Claude's answer"
    // We find that pattern and extract just the answer part.
    // ============================================================

    private static String extractTextFromResponse(String json) {

        // Look for the pattern "text": " in the JSON string.
        // This is the marker right before Claude's actual answer.
        String marker = "\"text\": \"";
        // Another common format from the API (no space after colon)
        String markerAlt = "\"text\":\"";

        int startIndex = json.indexOf(marker);
        int markerLength = marker.length();

        // Try alternate format if first format not found
        if (startIndex == -1) {
            startIndex = json.indexOf(markerAlt);
            markerLength = markerAlt.length();
        }

        // If we can't find the marker at all, something unexpected happened.
        if (startIndex == -1) {
            return "ERROR: Could not parse response.\nRaw response: " + json;
        }

        // Move past the marker to where the actual text begins.
        startIndex += markerLength;

        // Now find where the text ENDS.
        // We need to find the closing quote, but we have to be careful:
        // Claude's answer might contain escaped quotes like \"
        // So we look for a quote that is NOT preceded by a backslash.
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char current = json.charAt(endIndex);

            if (current == '"') {
                // Check if this quote is escaped (preceded by a backslash).
                // Count consecutive backslashes before this quote.
                int backslashCount = 0;
                int checkIndex = endIndex - 1;
                while (checkIndex >= startIndex && json.charAt(checkIndex) == '\\') {
                    backslashCount++;
                    checkIndex--;
                }
                // If even number of backslashes (including zero), the quote is real.
                // If odd number, the quote is escaped and we should keep going.
                if (backslashCount % 2 == 0) {
                    break;  // Found the real closing quote
                }
            }
            endIndex++;
        }

        // Extract just Claude's answer between those two positions.
        String extracted = json.substring(startIndex, endIndex);

        // Unescape common JSON escape sequences so the output reads naturally.
        // In JSON, special characters are "escaped" with backslashes.
        // We convert them back to the actual characters.
        extracted = extracted.replace("\\n", "\n");     // newline
        extracted = extracted.replace("\\t", "\t");     // tab
        extracted = extracted.replace("\\\"", "\"");    // double quote
        extracted = extracted.replace("\\\\", "\\");    // backslash itself (must be last)

        return extracted;
    }
}
