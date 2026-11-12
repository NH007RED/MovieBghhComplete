package com.moviebox.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.CategoryType
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.StatusPillBadge
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite

@Composable
fun AdminCategoriesScreen(
    categories: List<Category>,
    countUsage: (String) -> Int,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Categories (${categories.size})",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    editingCategory = Category(
                        id = "",
                        name = "",
                        type = CategoryType.BOTH.name,
                        active = true,
                        order = categories.size + 1
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            if (categories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No categories created yet. Tap '+ Add Category' above.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(categories, key = { _, c -> c.id }) { idx, cat ->
                        val typeLabel = when (cat.type) {
                            CategoryType.MOVIE.name -> "Movie"
                            CategoryType.SERIES.name -> "Web Series"
                            else -> "Both (Movie & Series)"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$typeLabel • Order #${cat.order}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            StatusPillBadge(active = cat.active, activeLabel = "Active", inactiveLabel = "Hidden")
                            Spacer(Modifier.width(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            onSaveCategory(cat.copy(order = (cat.order - 1).coerceAtLeast(0)))
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { editingCategory = cat },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextWhite, modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = { deletingCategory = cat },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    val editCat = editingCategory
    if (editCat != null) {
        var name by remember(editCat) { mutableStateOf(editCat.name) }
        var type by remember(editCat) { mutableStateOf(editCat.type) }
        var orderText by remember(editCat) { mutableStateOf(editCat.order.toString()) }
        var active by remember(editCat) { mutableStateOf(editCat.active) }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text(if (editCat.id.isBlank()) "Add Category" else "Edit Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Category Type", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryType.entries.forEach { ct ->
                            FilterPill(
                                label = ct.label,
                                selected = type == ct.name,
                                onClick = { type = ct.name }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = orderText,
                        onValueChange = { orderText = it },
                        label = { Text("Display Order") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active in User Catalog", color = TextWhite)
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCategory(
                            editCat.copy(
                                name = name.trim(),
                                type = type,
                                order = orderText.toIntOrNull() ?: editCat.order,
                                active = active
                            )
                        )
                        editingCategory = null
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val delCat = deletingCategory
    if (delCat != null) {
        val usedByCount = countUsage(delCat.id)
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete '${delCat.name}'?") },
            text = {
                Text(
                    if (usedByCount > 0) {
                        "Warning: '${delCat.name}' is currently assigned to $usedByCount movie(s)/series. Deleting it will remove this category tag from those records."
                    } else {
                        "Are you sure you want to permanently delete '${delCat.name}' from Firebase Realtime Database?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(delCat.id)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
