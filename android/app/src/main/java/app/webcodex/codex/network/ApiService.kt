package app.webcodex.codex.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

data class WorkspaceBase(val name: String, val path: String)
data class WorkspaceDir(val name: String, val path: String)
data class WorkspacesResponse(val base: WorkspaceBase, val dirs: List<WorkspaceDir>)

data class WorkspaceTreeNode(val name: String, val path: String, val type: String, val children: List<WorkspaceTreeNode>)
data class WorkspaceFlatNode(val name: String, val path: String, val display: String)
data class WorkspaceTreeResponse(val root: WorkspaceBase, val tree: List<WorkspaceTreeNode>, val flat: List<WorkspaceFlatNode>)
data class WorkspaceSearchResponse(val results: List<WorkspaceFlatNode>)

interface ApiService {
    @GET
    suspend fun getWorkspaces(
        @Url url: String,
        @Header("Authorization") auth: String
    ): WorkspacesResponse

    @GET
    suspend fun getWorkspaceTree(
        @Url url: String,
        @Header("Authorization") auth: String
    ): WorkspaceTreeResponse

    @GET
    suspend fun searchWorkspaces(
        @Url url: String,
        @Header("Authorization") auth: String
    ): WorkspaceSearchResponse
}

// Retrofit requires a base URL; use a placeholder when constructing
const val RETROFIT_PLACEHOLDER_BASE = "http://localhost/"
