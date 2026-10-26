@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nabd.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SceneSocialScreen(vm:NabdViewModel,openTopic:(String)->Unit) {
    val accounts by vm.socialAccounts.collectAsStateWithLifecycle();val posts by vm.socialPosts.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")};var group by rememberSaveable{mutableStateOf("الكل")}
    var editor by remember{mutableStateOf<FollowedSocialAccount?>(null)};var adding by remember{mutableStateOf(false)}
    var embed by remember{mutableStateOf<Pair<String,String>?>(null)}
    embed?.let{(url,title)->MediaPlayer(MediaTarget("X",url),title,close={embed=null})}
    if(adding || editor!=null) SocialAccountDialog(vm,editor){adding=false;editor=null}
    LazyColumn(contentPadding=PaddingValues(SceneSpace.page),verticalArrangement=Arrangement.spacedBy(SceneSpace.gap)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("حسابات X المتابعة",style=MaterialTheme.typography.titleLarge)
            SearchField(query,{query=it},"ابحث عن حساب أو منشور محفوظ")
            Picker("مجموعة المتابعة",listOf("الكل")+accounts.map{it.groupName}.distinct(),group){group=it}
            Button(onClick={adding=true}){Icon(Icons.Outlined.Add,null);Text("إضافة حساب")}
            Caption("التضمين الرسمي يعرض المتاح فقط. لا يجمع نبض جميع المنشورات أو مشاهداتها وإعجاباتها ولا يراقب X آليًا. الحسابات المحمية أو الحجب قد تمنع العرض.")
        } }
        items(accounts.sortedWith(compareByDescending<FollowedSocialAccount>{it.favorite}.thenBy{it.name}).filter{(query.isBlank() || "${it.name} ${it.handle} ${it.category}".contains(query,true)) && (group=="الكل" || it.groupName==group)},key={it.id}){account->SceneCard {
            Row { Column(Modifier.weight(1f)){Text(account.name,style=MaterialTheme.typography.titleMedium);Caption("@${account.handle} • ${account.category} • ${account.groupName}")}
                IconButton(onClick={vm.task{vm.dao.saveSocialAccount(account.copy(favorite=!account.favorite))}}){Icon(if(account.favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,"تفضيل الحساب",tint=Accent)} }
            FilledTonalButton(onClick={embed="https://x.com/${account.handle}" to account.name}){Text("عرض المنشورات داخل نبض")}
            FlowRow { TextButton(onClick={editor=account}){Text("تعديل")};TextButton(onClick={vm.task{vm.dao.deleteSocialAccount(account.id)}}){Text("حذف الحساب")}}
        } }
        item { SceneCard {
            Text("احفظ منشورًا من X",style=MaterialTheme.typography.titleMedium)
            Caption("من تطبيق X اختر مشاركة ← نبض. يُحفظ الرابط والنص الذي تشاركه أو تكتبه، ولا يُخمن نص المنشور. يمكنك كذلك لصق الرابط هنا.")
            var link by rememberSaveable{mutableStateOf("")}
            OutlinedTextField(link,{link=it},Modifier.fillMaxWidth(),label={Text("رابط منشور")})
            TextButton(onClick={vm.acceptShare(link);link=""}){Text("حفظ وربط بموضوع")}
        } }
        item { Text("المنشورات المحفوظة",style=MaterialTheme.typography.titleLarge) }
        items(posts.filter{query.isBlank() || "${it.title} ${it.text} ${it.handle}".contains(query,true)},key={"post-${it.id}"}){post->ScenePostCard(vm,post,openTopic)}
        if(accounts.isEmpty() && posts.isEmpty())item{SceneEmpty("ابدأ بحساب أو منشور","لا تُعرض منشورات تجريبية. أضف حسابًا عامًا أو شارك رابطًا من X.")}
    }
}
@Composable private fun SocialAccountDialog(vm:NabdViewModel,existing:FollowedSocialAccount?,close:()->Unit) {
    var handle by remember{mutableStateOf(existing?.handle.orEmpty())};var name by remember{mutableStateOf(existing?.name.orEmpty())}
    var category by remember{mutableStateOf(existing?.category?:"أخبار عاجلة")};var group by remember{mutableStateOf(existing?.groupName?:"عام")};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text(if(existing==null)"إضافة حساب X" else "تعديل الحساب")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(handle,{handle=it},label={Text("اسم المستخدم أو رابط الحساب")})
        OutlinedTextField(name,{name=it},label={Text("اسم العرض")})
        Picker("التصنيف",listOf("أخبار عاجلة","مراسل","مؤسسة إعلامية","اقتصادي","تقني","عام"),category){category=it}
        OutlinedTextField(group,{group=it},label={Text("مجموعة المتابعة")})
        if(error.isNotBlank())Text(error,color=Accent)
    }},confirmButton={TextButton(onClick={vm.task{try{vm.social.saveAccount(handle,name,category,group,existing?.favorite?:false,existing?.id);close()}catch(e:Exception){error=safeError(e)}}}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable fun ScenePostCard(vm:NabdViewModel,post:SavedSocialPost,openTopic:(String)->Unit) {
    var embed by remember{mutableStateOf(false)}
    if(embed)MediaPlayer(MediaTarget("X",post.url),post.title,close={embed=false})
    SceneCard {
        Text(post.title.ifBlank{"منشور محفوظ"},style=MaterialTheme.typography.titleMedium)
        Caption("${if(post.handle.isBlank())"هوية الناشر غير متاحة من الرابط" else "@${post.handle}"} • حُفظ ${dateText(post.created)}")
        if(post.text.isNotBlank())Text(post.text.take(800)) else Caption("رابط محفوظ؛ نص المنشور غير متاح للتطبيق")
        if(post.personalNote.isNotBlank())Caption("ملاحظتك: ${post.personalNote}",Mint)
        FlowRow { TextButton(onClick={embed=true}){Text("عرض التضمين")};LinkButton(post.url,"فتح الأصل في X") }
        FlowRow {
            if(post.topicId.isNotBlank())TextButton(onClick={openTopic(post.topicId)}){Text("ملف الموضوع")}
            TextButton(onClick={vm.pendingSharedPost.value=SharedPostDraft(post.url,post.handle,post.text)}){Text("تعديل وربط")}
            TextButton(onClick={vm.task{vm.dao.deleteSocialPost(post.id)}}){Text("حذف")}
        }
    }
}
@Composable fun SaveSharedPostDialog(vm:NabdViewModel,draft:SharedPostDraft,close:()->Unit,openTopic:(String)->Unit) {
    val topics by vm.sceneTopics.collectAsStateWithLifecycle()
    val posts by vm.socialPosts.collectAsStateWithLifecycle()
    val old=posts.firstOrNull{it.url==draft.url}
    var title by remember(draft.url){mutableStateOf(old?.title.orEmpty())}
    var text by remember(draft.url){mutableStateOf(old?.text?:draft.sharedText)}
    var note by remember(draft.url){mutableStateOf(old?.personalNote.orEmpty())}
    var selected by remember(draft.url){mutableStateOf(old?.topicId.orEmpty())}
    var newTitle by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("حفظ منشور في نبض المشهد")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Caption("${draft.url}\nلا يُجلب نص الحساب أو بيانات تسجيل الدخول. النص أدناه مما أضفته أو شاركته أنت.")}
            item{OutlinedTextField(title,{title=it.take(1000)},label={Text("عنوان شخصي")})}
            item{OutlinedTextField(text,{text=it.take(30000)},label={Text("نص المنشور المتاح لك — اختياري")},minLines=3)}
            item{OutlinedTextField(note,{note=it.take(10000)},label={Text("ملاحظة شخصية — لا تُرسل إلى AI تلقائيًا")},minLines=2)}
            item{SceneTopicPicker(topics,selected,includeNew=true){selected=it}}
            if(selected=="new")item{OutlinedTextField(newTitle,{newTitle=it.take(500)},label={Text("عنوان الموضوع الجديد")})}
            if(error.isNotBlank())item{Text(error,color=Accent)}
        }
    },confirmButton={TextButton(onClick={vm.task{try {
        val topic=if(selected=="new")vm.topics.create(newTitle).id else selected
        vm.social.save(draft,title,text,note,topic);close();if(topic.isNotBlank())openTopic(topic)
    }catch(e:Exception){error=safeError(e)}}}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}
@Composable fun SceneTopicPicker(topics:List<SceneTopic>,selected:String,includeNew:Boolean=false,choose:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Column { OutlinedButton(onClick={expanded=true}){Text(when(selected){""->"دون ربط بموضوع";"new"->"موضوع جديد";else->topics.firstOrNull{it.id==selected}?.title?:"اختر موضوعًا"},maxLines=2)}
        DropdownMenu(expanded,{expanded=false},modifier=Modifier.heightIn(max=340.dp)) {
            DropdownMenuItem(text={Text("دون ربط")},onClick={choose("");expanded=false})
            if(includeNew)DropdownMenuItem(text={Text("إنشاء موضوع جديد")},onClick={choose("new");expanded=false})
            topics.forEach{topic->DropdownMenuItem(text={Text(topic.title,maxLines=2)},onClick={choose(topic.id);expanded=false})}
        }
    }
}

