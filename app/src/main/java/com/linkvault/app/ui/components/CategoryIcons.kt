package com.linkvault.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a user can assign to a category. Keys are what is stored in the database and backups. */
object CategoryIcons {
    val all: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "folder" to Icons.Outlined.Folder,
        "school" to Icons.Outlined.School,
        "work" to Icons.Outlined.Work,
        "person" to Icons.Outlined.Person,
        "group" to Icons.Outlined.Group,
        "shopping" to Icons.Outlined.ShoppingCart,
        "movie" to Icons.Outlined.Movie,
        "build" to Icons.Outlined.Build,
        "finance" to Icons.Outlined.AccountBalance,
        "star" to Icons.Outlined.Star,
        "book" to Icons.Outlined.MenuBook,
        "home" to Icons.Outlined.Home,
        "code" to Icons.Outlined.Code,
        "music" to Icons.Outlined.MusicNote,
        "travel" to Icons.Outlined.Flight,
        "heart" to Icons.Outlined.FavoriteBorder,
        "game" to Icons.Outlined.SportsEsports,
        "news" to Icons.Outlined.Article,
        "cloud" to Icons.Outlined.Cloud,
        "bookmark" to Icons.Outlined.BookmarkBorder
    )

    fun get(key: String): ImageVector = all[key] ?: Icons.Outlined.Folder
}
