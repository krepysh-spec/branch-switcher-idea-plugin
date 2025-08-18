# BranchSwitcher

<!-- Plugin description -->
IntelliJ Platform plugin for managing SSH configurations and branch switching. Provides a convenient tool window to manage SSH hosts, test connections, and integrate with IntelliJ's deployment settings.
<!-- Plugin description end -->

IntelliJ Platform plugin for managing SSH configurations and branch switching.

## Architecture

The plugin follows a clean architecture pattern with separated concerns:

```
src/main/kotlin/com/github/krepysh/branchswitcher/
├── BranchSwitcher.kt           # Main facade class
├── config/
│   └── SshConstants.kt         # Configuration constants
├── model/
│   └── SshHost.kt             # Data models
├── parser/
│   └── SshConfigParser.kt     # SSH config parsing logic
├── service/
│   └── SshConfigService.kt    # Business logic layer
├── util/
│   └── FileUtils.kt           # Utility functions
└── toolWindow/
    └── MyToolWindowFactory.kt # UI components
```

## Usage

```kotlin
val branchSwitcher = BranchSwitcher()

// Get all SSH hosts
val hosts = branchSwitcher.getSshHosts()

// Check if gateway is configured
if (!branchSwitcher.hasGatewayConfigured()) {
    branchSwitcher.setupGateway()
}
```

## Development

This project is built using the IntelliJ Platform Plugin Template.

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

### Running

```bash
./gradlew runIde
```