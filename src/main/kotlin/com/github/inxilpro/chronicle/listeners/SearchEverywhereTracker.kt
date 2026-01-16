package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.SearchEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import java.lang.reflect.Proxy

/**
 * Tracks Search Everywhere queries using defensive reflection-based access
 * to the internal SearchEverywhereUI.SEARCH_EVENTS topic.
 *
 * This implementation degrades gracefully if the internal API is unavailable
 * or changes in future IDE versions.
 */
class SearchEverywhereTracker(
    private val project: Project
) : Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private var isRegistered = false

    fun tryRegister() {
        if (isRegistered) return

        try {
            val topicClass = Class.forName("com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI")
            val topicField = topicClass.getDeclaredField("SEARCH_EVENTS")
            topicField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val topic = topicField.get(null) as Topic<Any>

            val listenerClass = Class.forName("com.intellij.ide.actions.searcheverywhere.SearchListener")

            val listener = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "searchFinished" -> {
                        handleSearchFinished(args)
                    }
                    "searchStarted", "elementsAdded", "elementsRemoved",
                    "contributorWaits", "contributorFinished" -> {
                        // Ignore these events
                    }
                    "equals" -> this == args?.getOrNull(0)
                    "hashCode" -> this.hashCode()
                    "toString" -> "SearchEverywhereTracker.Listener"
                }
                null
            }

            val connection = project.messageBus.connect(this)
            connection.subscribe(topic, listener)

            isRegistered = true
            thisLogger().info("Search Everywhere tracking registered successfully")

        } catch (e: ClassNotFoundException) {
            thisLogger().warn("Search Everywhere tracking unavailable: SearchEverywhereUI class not found")
        } catch (e: NoSuchFieldException) {
            thisLogger().warn("Search Everywhere tracking unavailable: SEARCH_EVENTS field not found")
        } catch (e: Exception) {
            thisLogger().warn("Search Everywhere tracking unavailable: ${e.message}")
        }
    }

    private fun handleSearchFinished(args: Array<Any?>?) {
        try {
            val contributors = args?.getOrNull(0) as? Map<*, *> ?: return

            for ((contributor, items) in contributors) {
                val searchQuery = extractSearchQuery(contributor)
                if (!searchQuery.isNullOrBlank()) {
                    transcriptService.log(SearchEvent(query = searchQuery))
                    break
                }
            }
        } catch (e: Exception) {
            thisLogger().debug("Failed to extract search query: ${e.message}")
        }
    }

    private fun extractSearchQuery(contributor: Any?): String? {
        if (contributor == null) return null

        return try {
            val contributorClass = contributor.javaClass

            val getSearchQueryMethod = contributorClass.methods.find {
                it.name == "getSearchQuery" || it.name == "getPattern" || it.name == "getSearchPattern"
            }
            if (getSearchQueryMethod != null) {
                getSearchQueryMethod.isAccessible = true
                return getSearchQueryMethod.invoke(contributor) as? String
            }

            val filterField = contributorClass.declaredFields.find {
                it.name == "filter" || it.name == "pattern" || it.name == "searchQuery"
            }
            if (filterField != null) {
                filterField.isAccessible = true
                return filterField.get(contributor) as? String
            }

            null
        } catch (e: Exception) {
            thisLogger().debug("Could not extract search query from contributor: ${e.message}")
            null
        }
    }

    override fun dispose() {
        isRegistered = false
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): SearchEverywhereTracker {
            val tracker = SearchEverywhereTracker(project)
            tracker.tryRegister()
            Disposer.register(parentDisposable, tracker)
            return tracker
        }
    }
}
