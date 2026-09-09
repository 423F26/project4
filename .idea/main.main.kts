fun main() {
    val username = "admin"
    val password = "secret123"

    print("Enter password: ")
    val enteredPassword = readLine() ?: ""

    val passwordMatch = enteredPassword == password

    if (passwordMatch) {
        println("Access granted, $username.")
    } else {
        println("Access denied.")
    }
}