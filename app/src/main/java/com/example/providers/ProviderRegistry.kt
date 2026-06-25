package com.example.providers

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central registry for LLM providers.
 * Manages registration, activation, and retrieval of providers.
 */
object ProviderRegistry {
    private const val TAG = "ProviderRegistry"
    
    private val providers = ConcurrentHashMap<String, LLMProvider>()
    @Volatile
    private var activeProviderName: String? = null
    private val mutex = Mutex()
    
    /**
     * Register a provider.
     */
    suspend fun register(provider: LLMProvider) {
        providers[provider.name] = provider
        Log.d(TAG, "Registered provider: ${provider.name}")
        
        mutex.withLock {
            // Auto-activate if this is the first provider
            if (activeProviderName == null) {
                activeProviderName = provider.name
                Log.d(TAG, "Auto-activated provider: ${provider.name}")
            }
        }
    }
    
    /**
     * Unregister a provider.
     */
    suspend fun unregister(name: String) {
        providers.remove(name)
        Log.d(TAG, "Unregistered provider: $name")
        
        mutex.withLock {
            // Clear active if it was removed
            if (activeProviderName == name) {
                activeProviderName = providers.keys.firstOrNull()
                Log.d(TAG, "Unregistered active provider, new active: $activeProviderName")
            }
        }
    }
    
    /**
     * Get a provider by name.
     */
    fun get(name: String): LLMProvider? = providers[name]
    
    /**
     * Get the currently active provider.
     */
    fun getActive(): LLMProvider? {
        return activeProviderName?.let { providers[it] }
    }
    
    /**
     * Set the active provider.
     */
    fun setActive(name: String): Boolean {
        if (providers.containsKey(name)) {
            activeProviderName = name
            Log.d(TAG, "Set active provider: $name")
            return true
        }
        Log.w(TAG, "Cannot set active provider: $name (not registered)")
        return false
    }
    
    /**
     * Get all registered provider names.
     */
    fun names(): List<String> = providers.keys.toList()
    
    /**
     * Get all registered providers.
     */
    fun all(): List<LLMProvider> = providers.values.toList()
    
    /**
     * Clear all providers.
     */
    suspend fun clear() {
        providers.clear()
        mutex.withLock {
            activeProviderName = null
        }
        Log.d(TAG, "Cleared all providers")
    }
    
    /**
     * Check if any provider is registered.
     */
    fun hasProviders(): Boolean = providers.isNotEmpty()
    
    /**
     * Get providers that support a specific capability.
     */
    fun withCapability(capability: ProviderCapability): List<LLMProvider> {
        return providers.values.filter { it.supports(capability) }
    }
}
