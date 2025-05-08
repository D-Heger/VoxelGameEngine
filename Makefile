# Detect operating system and set appropriate gradle wrapper command
# Make sure to have gradle in path on Windows
ifeq ($(OS),Windows_NT)
	GRADLE := gradle
else
	GRADLE := ./gradlew
endif

.PHONY: all build clean test run package

all: ## Default target: builds the project
	@$(MAKE) build

build: ## Build the project
	@echo "Building the project..."
	@$(GRADLE) build --console=rich --warning-mode=all

clean: ## Clean the project build directories
	@echo "Cleaning the project..."
	@$(GRADLE) clean

test: ## Run tests for all subprojects with detailed output
	@echo "Running tests..."
	@$(GRADLE) test

run: ## Run the game launcher (requires configuration)
	@echo "Running the launcher..."
	@$(GRADLE) :launcher:run

fat-jar: ## Package the application into a distributable fat JAR
	@echo "Packaging the application into a fat JAR..."
	@$(GRADLE) :launcher:shadowJar --console=rich