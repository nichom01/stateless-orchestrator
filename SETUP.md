# Setup Instructions

## Prerequisites

- Java 17+ (✅ You have Java installed)
- Maven 3.6+ (❌ Not found in PATH)

## Installing Maven

### Option 1: Using Homebrew (macOS - Recommended)

```bash
brew install maven
```

After installation, verify:
```bash
mvn --version
```

### Option 2: Manual Installation

1. Download Maven from https://maven.apache.org/download.cgi
2. Extract to a directory (e.g., `/usr/local/apache-maven-3.9.x`)
3. Add to your PATH by editing `~/.zshrc`:
   ```bash
   export PATH="/usr/local/apache-maven-3.9.x/bin:$PATH"
   ```
4. Reload your shell:
   ```bash
   source ~/.zshrc
   ```

### Option 3: Using SDKMAN

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install maven
```

## Running the Application

Once Maven is installed:

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/stateless-orchestrator-1.0.0.jar
```

## Alternative: Using Your IDE

If you're using IntelliJ IDEA or Eclipse:

1. **IntelliJ IDEA:**
   - Right-click on `pom.xml` → "Add as Maven Project"
   - Right-click on `StatelessOrchestratorApplication.java` → "Run"

2. **VS Code:**
   - Install the "Extension Pack for Java"
   - Open the project folder
   - Use the Run/Debug button in the editor

3. **Eclipse:**
   - File → Import → Existing Maven Projects
   - Right-click project → Run As → Spring Boot App

## Troubleshooting

### Maven still not found after installation

Check your PATH:
```bash
echo $PATH
```

Make sure Maven's `bin` directory is included. If using Homebrew, it should be automatically added.

### Port 8080 already in use

Change the port in `application.yml` or stop the other service:
```bash
lsof -ti:8080 | xargs kill -9
```
