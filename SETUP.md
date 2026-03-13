# 🚀 Setup Guide — From Zero to Running Plugin

This guide walks you through creating the GitHub repo, building the plugin, and installing it on your server.

---

## Step 1: Create the GitHub Repository

### Option A: Using GitHub CLI (fastest)

```bash
# Install GitHub CLI if you haven't
brew install gh          # macOS
# or: https://cli.github.com/ for other platforms

# Authenticate (one-time)
gh auth login

# Navigate to the project folder
cd /path/to/bounty-smp

# Create the repo and push everything in one go
git init
git add .
git commit -m "Initial commit — Bounty SMP plugin with 5 custom weapons"
gh repo create bounty-smp --public --source=. --push
```

### Option B: Using the GitHub website

1. Go to [github.com/new](https://github.com/new)
2. **Repository name**: `bounty-smp`
3. **Description**: `Bounty SMP — Minecraft Paper plugin with custom weapons and bounty system`
4. Set to **Public** or **Private**
5. **Do NOT** add a README, .gitignore, or license (we already have those)
6. Click **Create repository**
7. Then run these commands in your terminal:

```bash
cd /path/to/bounty-smp
git init
git add .
git commit -m "Initial commit — Bounty SMP plugin with 5 custom weapons"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/bounty-smp.git
git push -u origin main
```

---

## Step 2: Install Java 21

### macOS
```bash
brew install openjdk@21
```

### Windows
Download from [Adoptium](https://adoptium.net/temurin/releases/?version=21) and run the installer.

### Linux
```bash
sudo apt install openjdk-21-jdk    # Debian/Ubuntu
sudo dnf install java-21-openjdk   # Fedora
```

Verify:
```bash
java -version
# Should show: openjdk version "21.x.x"
```

---

## Step 3: Install Gradle & Generate the Wrapper

The `gradle-wrapper.jar` is a binary and is excluded from Git. You need to generate it once:

### macOS
```bash
brew install gradle
```

### Windows
```bash
choco install gradle
```

### Linux (SDKMAN — recommended)
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5
```

### Generate the wrapper
```bash
cd bounty-smp
gradle wrapper --gradle-version 8.5
```

This creates `gradle/wrapper/gradle-wrapper.jar`. You only do this once.

---

## Step 4: Build the Plugin

```bash
# macOS / Linux
chmod +x ./gradlew
./gradlew build

# Windows
gradlew.bat build
```

The compiled JAR will be at:
```
build/libs/playergamespaperplugin-0.1.0.jar
```

---

## Step 5: Install on Your Server

```bash
cp build/libs/playergamespaperplugin-0.1.0.jar /path/to/server/plugins/
```

Then restart the server, or if you have a plugin manager:
```
/reload confirm
```

---

## Step 6: Verify It's Working

1. Join the server
2. Run `/playergames` — you should see the help menu
3. Craft one of the weapons using the default recipes (see README.md)
4. Test the Charge Bow by shooting other players with arrows

---

## Troubleshooting

### `permission denied: ./gradlew`
```bash
chmod +x ./gradlew
```

### `Unable to access jarfile gradle-wrapper.jar`
You need to generate the wrapper first:
```bash
gradle wrapper --gradle-version 8.5
```

### `Could not resolve: io.papermc.paper:paper-api`
Make sure you have internet access. Gradle needs to download the Paper API dependency the first time you build.

### Plugin doesn't load / `ClassNotFoundException`
Make sure you're running **Paper 1.21.x** (not vanilla or older versions). Check the server log for errors.

### `JAVA_HOME is not set`
Set it to your Java 21 installation:
```bash
# macOS (Homebrew)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Linux
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Add to your shell profile (~/.zshrc, ~/.bashrc) to make it permanent
```

---

## Making Changes

1. Edit the Java files in `src/main/java/com/playergames/paper/`
2. Rebuild: `./gradlew build`
3. Copy the new JAR to your server's `plugins/` folder
4. Restart the server
5. Commit & push:
   ```bash
   git add .
   git commit -m "Description of changes"
   git push
   ```
