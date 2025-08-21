# DataSource Naming Convention

## Overview

The DataSource service has been updated to use project names instead of host names for database connection names. This change ensures consistency with other IntelliJ IDEA configurations (SSH, Deployment) and provides more meaningful names for database connections.

## Changes Made

### DataSourceService.kt

1. **Method Signature Update**: 
   - `createDataSourceForProjects()` now accepts a `projectName` parameter
   - The database connection name is generated based on the project name, not the host name

2. **Unique Name Generation**:
   - Added `generateUniqueDataSourceName()` method to create unique names
   - If a project name already exists, it appends a counter (e.g., "project_2", "project_3")
   - Prevents naming conflicts across different projects

3. **Enhanced Removal**:
   - Added `removeDataSourceFromProjectsByProjectName()` method
   - Can remove DataSource by project name instead of just host name

### SshHostsToolWindowFactory.kt

1. **Updated Method Calls**:
   - Both `createDataSourceForProjects()` calls now pass the project name
   - First call: uses `selectedProject` from the dialog
   - Second call: uses `originalProject` from the duplicated host

## How It Works

### Before (Old Behavior)
```kotlin
// Database connection was named after the host
dataSourceService.createDataSourceForProjects(
    hostName,      // "server-01"
    hostname,      // "192.168.1.100"
    port,          // 5432
    dbName,        // "production_db"
    username       // "dbuser"
)
// Result: DataSource named "server-01"
```

### After (New Behavior)
```kotlin
// Database connection is named after the project
dataSourceService.createDataSourceForProjects(
    hostName,      // "server-01"
    hostname,      // "192.168.1.100"
    port,          // 5432
    dbName,        // "production_db"
    username,      // "dbuser"
    projectName    // "my-awesome-project"
)
// Result: DataSource named "my-awesome-project"
```

## Benefits

1. **Consistency**: Database connections now follow the same naming pattern as SSH and Deployment configurations
2. **Clarity**: Project-based names are more meaningful than host-based names
3. **Organization**: Easier to identify which database connection belongs to which project
4. **Conflict Prevention**: Automatic generation of unique names prevents naming conflicts

## Example Scenarios

### Scenario 1: New Project
- Project: "ecommerce-app"
- Host: "db-server-01"
- Result: DataSource named "ecommerce-app"

### Scenario 2: Duplicate Project Name
- First project: "api-service" → DataSource named "api-service"
- Second project: "api-service" → DataSource named "api-service_2"

### Scenario 3: Multiple Hosts, Same Project
- Project: "backend"
- Host 1: "db-primary" → DataSource named "backend"
- Host 2: "db-replica" → DataSource named "backend_2"

## Migration

Existing DataSource configurations will continue to work. New configurations created after this update will use the new naming convention. If you need to rename existing DataSource configurations, you can:

1. Remove the old DataSource using the old host name
2. Recreate it using the new project-based naming system

## Technical Details

The unique name generation algorithm:
1. Starts with the base project name
2. Checks all existing DataSource names across all projects
3. If a conflict is found, appends "_2", "_3", etc.
4. Continues until a unique name is found

This ensures that no two DataSource configurations have the same name, even across different projects. 