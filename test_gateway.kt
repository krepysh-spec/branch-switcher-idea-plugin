import com.github.krepysh.branchswitcher.parser.SshConfigParser
import java.io.File

fun main() {
    val parser = SshConfigParser()
    
    // Test if gateway host exists
    println("Gateway host exists: ${parser.hasGatewayHost()}")
    
    // Test default identity file
    println("Default identity file: ${parser.getDefaultIdentityFile()}")
    
    // Test adding gateway host
    if (!parser.hasGatewayHost()) {
        println("Adding gateway host...")
        parser.addGatewayHost("~/.ssh/id_rsa")
        println("Gateway host added!")
    }
    
    // Parse and display all hosts
    val hosts = parser.parseConfig()
    println("\nAll hosts:")
    hosts.forEach { host ->
        println("- ${host.name}: ${host.hostname} (${host.user}) [${host.identityFile}]")
    }
}