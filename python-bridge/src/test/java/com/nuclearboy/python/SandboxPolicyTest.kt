package com.nuclearboy.python

import org.junit.Assert.*
import org.junit.Test

class SandboxPolicyTest {

    private val enforcer = PolicyEnforcer()

    @Test
    fun `strict policy denies network`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.NetworkAccess)
        assertTrue(result.isFailure)
    }

    @Test
    fun `strict policy denies shell`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.ShellAccess)
        assertTrue(result.isFailure)
    }

    @Test
    fun `standard policy allows workspace read`() {
        val policy = SandboxPolicy.standard()
        val result = enforcer.validate(policy, SandboxOperation.ReadFile("workspace/test.py"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `standard policy allows workspace write`() {
        val policy = SandboxPolicy.standard()
        val result = enforcer.validate(policy, SandboxOperation.WriteFile("workspace/output.txt"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `path traversal is blocked`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.ReadFile("../etc/passwd"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `path traversal with encoding is blocked`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.ReadFile("workspace/../../etc/passwd"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `package not in allowlist is denied`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.InstallPackage("malicious-pkg"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `package in allowlist is permitted`() {
        val policy = SandboxPolicy(
            allowedReadPaths = listOf("workspace/**"),
            allowedWritePaths = listOf("workspace/**"),
            networkAllowed = false,
            allowedPackages = listOf("python-docx", "openpyxl"),
            shellAllowed = false,
        )
        val result = enforcer.validate(policy, SandboxOperation.InstallPackage("python-docx"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `document generation policy permits network`() {
        val policy = SandboxPolicy.documentGeneration()
        val result = enforcer.validate(policy, SandboxOperation.NetworkAccess)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `relaxed policy permits shell`() {
        val policy = SandboxPolicy.relaxed()
        val result = enforcer.validate(policy, SandboxOperation.ShellAccess)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `build environment sets policy variables`() {
        val policy = SandboxPolicy.standard()
        val env = enforcer.buildEnvironment(policy)
        assertTrue(env.containsKey("NUCLEARBOY_NETWORK_ALLOWED"))
        assertEquals("false", env["NUCLEARBOY_NETWORK_ALLOWED"])
    }

    @Test
    fun `validate returns error detail on failure`() {
        val policy = SandboxPolicy.strict()
        val result = enforcer.validate(policy, SandboxOperation.ShellAccess)
        assertTrue(result.isFailure)
        val failure = result as com.nuclearboy.common.AppResult.Failure
        assertTrue(failure.error.humanMessage.isNotBlank())
    }
}
