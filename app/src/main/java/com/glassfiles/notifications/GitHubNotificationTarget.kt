package com.glassfiles.notifications

import android.net.Uri

data class GitHubNotificationTarget(
    val repoFullName: String,
    val subjectType: String,
    val number: Int? = null
) {
    val owner: String get() = repoFullName.substringBefore('/', "")
    val repo: String get() = repoFullName.substringAfter('/', "")

    companion object {
        fun from(
            subjectUrl: String,
            htmlUrl: String,
            repoFullName: String,
            subjectType: String
        ): GitHubNotificationTarget? {
            val parsedRepo = repoFullName.ifBlank {
                parseRepoFullName(subjectUrl).ifBlank { parseRepoFullName(htmlUrl) }
            }
            if ('/' !in parsedRepo) return null
            val inferredType = subjectType.ifBlank {
                when {
                    hasPathMarker(subjectUrl, "pulls") || hasPathMarker(htmlUrl, "pull") -> "PullRequest"
                    hasPathMarker(subjectUrl, "issues") || hasPathMarker(htmlUrl, "issues") -> "Issue"
                    hasPathMarker(subjectUrl, "releases") || hasPathMarker(htmlUrl, "releases") -> "Release"
                    hasPathMarker(subjectUrl, "discussions") || hasPathMarker(htmlUrl, "discussions") -> "Discussion"
                    else -> ""
                }
            }
            return GitHubNotificationTarget(
                repoFullName = parsedRepo,
                subjectType = inferredType,
                number = parseNumber(subjectUrl) ?: parseNumber(htmlUrl)
            )
        }

        private fun parseRepoFullName(url: String): String {
            val segments = pathSegments(url)
            val repoIndex = segments.indexOf("repos")
            if (repoIndex >= 0 && segments.size > repoIndex + 2) {
                return "${segments[repoIndex + 1]}/${segments[repoIndex + 2]}"
            }
            if (segments.size >= 2) return "${segments[0]}/${segments[1]}"
            return ""
        }

        private fun parseNumber(url: String): Int? {
            val segments = pathSegments(url)
            val markerIndex = segments.indexOfFirst {
                it == "issues" || it == "pulls" || it == "pull" || it == "releases" || it == "discussions"
            }
            return segments.getOrNull(markerIndex + 1)?.toIntOrNull()
        }

        private fun hasPathMarker(url: String, marker: String): Boolean =
            marker in pathSegments(url)

        private fun pathSegments(url: String): List<String> = try {
            Uri.parse(url).pathSegments.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
