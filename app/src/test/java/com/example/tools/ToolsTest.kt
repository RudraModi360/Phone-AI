package com.example.tools

import com.example.tools.builtin.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for tool implementations.
 * These tests verify that tools work correctly on Android/Termux.
 */
class ToolsTest {
    
    @Before
    fun setup() {
        ToolRegistry.clear()
        ToolRegistry.initializeDefaults()
    }
    
    // ============ Registry Tests ============
    
    @Test
    fun `registry initializes with all default tools`() {
        val tools = ToolRegistry.all()
        assertTrue("Should have at least 5 tools", tools.size >= 5)
        
        assertNotNull("datetime tool should exist", ToolRegistry.get("datetime"))
        assertNotNull("read_file tool should exist", ToolRegistry.get("read_file"))
        assertNotNull("list_dir tool should exist", ToolRegistry.get("list_dir"))
        assertNotNull("shell_exec tool should exist", ToolRegistry.get("shell_exec"))
        assertNotNull("write_file tool should exist", ToolRegistry.get("write_file"))
    }
    
    @Test
    fun `safe tools are correctly identified`() {
        val safeTools = ToolRegistry.safeTools()
        val safeNames = safeTools.map { it.name }
        
        assertTrue("datetime should be safe", "datetime" in safeNames)
        assertTrue("read_file should be safe", "read_file" in safeNames)
        assertTrue("list_dir should be safe", "list_dir" in safeNames)
    }
    
    @Test
    fun `dangerous tools are correctly identified`() {
        val dangerousTools = ToolRegistry.dangerousTools()
        val dangerousNames = dangerousTools.map { it.name }
        
        assertTrue("shell_exec should be dangerous", "shell_exec" in dangerousNames)
        assertTrue("write_file should be dangerous", "write_file" in dangerousNames)
    }
    
    // ============ DateTimeTool Tests ============
    
    @Test
    fun `datetime returns current time`() = runBlocking {
        val tool = DateTimeTool()
        val result = tool.execute(emptyMap())
        
        assertTrue("Should succeed", result.success)
        assertNotNull("Should have content", result.content)
        assertTrue("Content should not be empty", result.content!!.isNotEmpty())
    }
    
    @Test
    fun `datetime handles timezone`() = runBlocking {
        val tool = DateTimeTool()
        val result = tool.execute(mapOf("timezone" to "America/New_York"))
        
        assertTrue("Should succeed", result.success)
    }
    
    // ============ ListDirTool Tests ============
    
    @Test
    fun `list_dir works with no path`() = runBlocking {
        val tool = ListDirTool()
        val result = tool.execute(emptyMap())
        
        // May fail if no home dir accessible, but shouldn't crash
        assertNotNull("Should return a result", result)
    }
    
    @Test
    fun `list_dir handles nonexistent path`() = runBlocking {
        val tool = ListDirTool()
        val result = tool.execute(mapOf("path" to "/nonexistent/path/12345"))
        
        assertFalse("Should fail for nonexistent path", result.success)
        assertTrue("Error should mention not found", result.error?.contains("not found") == true)
    }
    
    @Test
    fun `list_dir respects show_hidden`() = runBlocking {
        val tool = ListDirTool()
        
        // Without hidden
        val resultWithoutHidden = tool.execute(mapOf("path" to "/tmp", "show_hidden" to false))
        
        // With hidden
        val resultWithHidden = tool.execute(mapOf("path" to "/tmp", "show_hidden" to true))
        
        // Both should either succeed or fail consistently
        assertEquals(resultWithoutHidden.success, resultWithHidden.success)
    }
    
    // ============ FileReadTool Tests ============
    
    @Test
    fun `read_file handles missing path`() = runBlocking {
        val tool = FileReadTool()
        val result = tool.execute(emptyMap())
        
        assertFalse("Should fail without path", result.success)
        assertTrue("Error should mention path", result.error?.contains("path") == true)
    }
    
    @Test
    fun `read_file handles nonexistent file`() = runBlocking {
        val tool = FileReadTool()
        val result = tool.execute(mapOf("path" to "/nonexistent/file.txt"))
        
        assertFalse("Should fail for nonexistent file", result.success)
        assertTrue("Error should mention not found", result.error?.contains("not found") == true || result.error?.contains("denied") == true)
    }
    
    @Test
    fun `read_file blocks system paths`() = runBlocking {
        val tool = FileReadTool()
        val result = tool.execute(mapOf("path" to "/system/build.prop"))
        
        assertFalse("Should block system path", result.success)
        assertTrue("Error should mention denied", result.error?.contains("denied") == true)
    }
    
    // ============ ShellExecTool Tests ============
    
    @Test
    fun `shell_exec handles missing command`() = runBlocking {
        val tool = ShellExecTool()
        val result = tool.execute(emptyMap())
        
        assertFalse("Should fail without command", result.success)
        assertTrue("Error should mention command", result.error?.contains("command") == true)
    }
    
    @Test
    fun `shell_exec blocks dangerous commands`() = runBlocking {
        val tool = ShellExecTool()
        
        val dangerousCommands = listOf(
            "rm -rf /",
            "rm -rf *",
            "dd if=/dev/zero of=/dev/sda",
            "chmod 777 /system"
        )
        
        for (cmd in dangerousCommands) {
            val result = tool.execute(mapOf("command" to cmd))
            assertFalse("Should block: $cmd", result.success)
            assertTrue("Error should mention blocked", result.error?.contains("blocked") == true)
        }
    }
    
    @Test
    fun `shell_exec identifies safe commands`() {
        val safeCommands = listOf("pwd", "whoami", "date", "echo hello", "ls")
        
        for (cmd in safeCommands) {
            assertTrue("$cmd should be safe", ShellExecTool.isCommandSafe(cmd))
        }
    }
    
    // ============ WriteFileTool Tests ============
    
    @Test
    fun `write_file handles missing args`() = runBlocking {
        val tool = WriteFileTool()
        
        val result1 = tool.execute(emptyMap())
        assertFalse("Should fail without path", result1.success)
        
        val result2 = tool.execute(mapOf("path" to "test.txt"))
        assertFalse("Should fail without content", result2.success)
    }
    
    @Test
    fun `write_file blocks system paths`() = runBlocking {
        val tool = WriteFileTool()
        val result = tool.execute(mapOf(
            "path" to "/system/test.txt",
            "content" to "test"
        ))
        
        assertFalse("Should block system path", result.success)
        assertTrue("Error should mention denied", result.error?.contains("denied") == true)
    }
    
    @Test
    fun `write_file blocks dangerous extensions`() = runBlocking {
        val tool = WriteFileTool()
        val result = tool.execute(mapOf(
            "path" to "malware.apk",
            "content" to "fake apk content"
        ))
        
        assertFalse("Should block .apk files", result.success)
    }
    
    // ============ Integration Tests ============
    
    @Test
    fun `tool descriptions are generated correctly`() {
        val descriptions = ToolRegistry.getToolDescriptions()
        
        assertTrue("Should mention datetime", descriptions.contains("datetime"))
        assertTrue("Should mention read_file", descriptions.contains("read_file"))
        assertTrue("Should mention shell_exec", descriptions.contains("shell_exec"))
        assertTrue("Should describe parameters", descriptions.contains("command") || descriptions.contains("path"))
    }
    
    @Test
    fun `isSafeTool returns correct values`() {
        assertTrue("datetime is safe", ToolRegistry.isSafeTool("datetime"))
        assertTrue("read_file is safe", ToolRegistry.isSafeTool("read_file"))
        assertTrue("list_dir is safe", ToolRegistry.isSafeTool("list_dir"))
        
        assertFalse("shell_exec is not safe", ToolRegistry.isSafeTool("shell_exec"))
        assertFalse("write_file is not safe", ToolRegistry.isSafeTool("write_file"))
        assertFalse("unknown tool returns false", ToolRegistry.isSafeTool("nonexistent"))
    }
}
