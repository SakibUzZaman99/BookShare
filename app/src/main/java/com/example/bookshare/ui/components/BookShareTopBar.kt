package com.example.bookshare.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.bookshare.R

@Composable
fun BookShareTopBar(
    navController: NavController,
    title: String // 👈 Dynamic title for each screen
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.booksharelogo),
                    contentDescription = "BookShare Logo",
                    modifier = Modifier
                        .size(55.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            var expanded by remember { mutableStateOf(false) }
            Box {
                Image(
                    painter = painterResource(id = R.drawable.default_profile),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            CircleShape
                        )
                        .clickable { expanded = !expanded }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(180.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("👤 My Profile") },
                        onClick = {
                            expanded = false
                            navController.navigate("profile")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📚 My Library") },
                        onClick = {
                            expanded = false
                            navController.navigate("library")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🏠 Home") },
                        onClick = {
                            expanded = false
                            navController.navigate("home")
                        }
                    )
                }
            }
        }
    }
}
