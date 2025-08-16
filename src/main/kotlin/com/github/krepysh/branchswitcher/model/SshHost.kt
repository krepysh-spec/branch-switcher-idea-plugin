package com.github.krepysh.branchswitcher.model

data class SshHost(
    val name: String,
    val hostname: String? = null,
    val user: String? = null,
    val port: Int? = null,
    val identityFile: String? = null,
    val proxyCommand: String? = null,
    val otherProperties: Map<String, String> = emptyMap()
)