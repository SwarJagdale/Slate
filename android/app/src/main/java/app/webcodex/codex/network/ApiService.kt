package app.webcodex.codex.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

data class WorkspaceBase(val name: String, val path: String)
data class WorkspaceDir(val name: String, val path: String)
data class WorkspacesResponse(val base: WorkspaceBase, val dirs: List<WorkspaceDir>)

interface ApiService {
    @GET
    suspend fun getWorkspaces(
        @Url url: String,
        @Header("Authorization") auth: String
    ): WorkspacesResponse
}

// Retrofit requires a base URL; use a placeholder when constructing
const val RETROFIT_PLACEHOLDER_BASE = "http://localhost/"
