package com.nabd.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

object SceneSpace { val page=20.dp;val gap=14.dp;val radius=24.dp }
@Composable fun SceneCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(SceneSpace.radius),color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun SceneLoading() {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        repeat(3) { Surface(shape=RoundedCornerShape(SceneSpace.radius),color=MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(.65f).height(16.dp).background(MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(8.dp)))
                Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(8.dp)))
            }
        } }
        Caption("جارٍ فحص مصادر الرصد؛ تبقى البيانات المحفوظة قابلة للقراءة")
    }
}
@Composable fun SceneEmpty(title:String,body:String,action:String="",click:()->Unit={}) {
    SceneCard {
        Icon(Icons.Outlined.TravelExplore,null,tint=Accent)
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(body,style=MaterialTheme.typography.bodyMedium,color=Muted)
        if(action.isNotBlank())FilledTonalButton(onClick=click){Text(action)}
    }
}

