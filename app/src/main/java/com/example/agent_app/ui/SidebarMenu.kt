package com.example.agent_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarMenu(
    selectedMenu: DrawerMenu,
    onMenuSelected: (DrawerMenu) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 햄버거 아이콘 (맨 위)
        IconButton(
            onClick = onCloseDrawer,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "닫기",
            )
        }
        
        Divider()
        
        // 메뉴 항목들
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 메뉴
            Text(
                text = "메뉴",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selectedMenu == DrawerMenu.Menu) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedMenu == DrawerMenu.Menu) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMenuSelected(DrawerMenu.Menu) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            
            Divider()
            
            // 개발자 기능
            Text(
                text = "개발자 기능",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selectedMenu == DrawerMenu.Developer) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedMenu == DrawerMenu.Developer) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMenuSelected(DrawerMenu.Developer) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            
            Divider()
        }
        
        // 나머지 빈 공간
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // 빈 공간
        }
    }
}

